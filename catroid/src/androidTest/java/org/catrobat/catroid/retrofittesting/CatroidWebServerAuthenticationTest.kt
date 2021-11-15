/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.retrofittesting

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.koin.testModules
import org.catrobat.catroid.retrofit.WebService
import org.catrobat.catroid.retrofit.models.DeprecatedToken
import org.catrobat.catroid.retrofit.models.LoginUser
import org.catrobat.catroid.retrofit.models.RegisterFailedResponse
import org.catrobat.catroid.retrofit.models.RegisterUser
import org.catrobat.catroid.testsuites.annotations.Cat.OutgoingNetworkTests
import org.catrobat.catroid.web.ServerAuthenticationConstants.SERVER_RESPONSE_INVALID_UPLOAD_TOKEN
import org.catrobat.catroid.web.ServerAuthenticationConstants.SERVER_RESPONSE_REGISTER_OK
import org.catrobat.catroid.web.ServerAuthenticationConstants.SERVER_RESPONSE_REGISTER_UNPROCESSABLE_ENTITY
import org.catrobat.catroid.web.ServerAuthenticationConstants.SERVER_RESPONSE_TOKEN_OK
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Category(OutgoingNetworkTests::class)
class CatroidWebServerAuthenticationTest : KoinTest {

    companion object {
        private const val STATUS_CODE_INVALID_CREDENTIALS = 401
    }

    private lateinit var newEmail: String
    private lateinit var newUserName: String
    private var email = "catroweb@localhost.at"
    private var username = "catroweb"
    private var invalidUsername = "InvalidUser"
    private var password = "catroweb"
    private var wrongPassword = "WrongPassword"
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    private val webServer: WebService by inject()

    @Before
    fun setUp() {
        stopKoin()
        startKoin { modules(testModules) }

        newUserName = "APIUser" + System.currentTimeMillis()
        newEmail = "$newUserName@api.at"

        context = InstrumentationRegistry.getInstrumentation().context
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testLoginWithInvalidCredentials() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val actualResponse = webServer.login("Bearer $token", LoginUser(invalidUsername, password)).execute()
        assertEquals(actualResponse.code(), STATUS_CODE_INVALID_CREDENTIALS)
    }

    @Test
    fun testLoginWrongPassword() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val actualResponse = webServer.login("Bearer $token", LoginUser(username, wrongPassword)).execute()
        assertEquals(actualResponse.code(), STATUS_CODE_INVALID_CREDENTIALS)
    }

    @Test
    fun testLoginOk() {
        var token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val response = webServer.login("Bearer $token", LoginUser(username, password)).execute()

        assertEquals(response.code(), SERVER_RESPONSE_TOKEN_OK)

        val responseBody = response.body()
        assertNotNull(responseBody)
        assertNotNull(responseBody?.token)

        token = responseBody?.token
        val responseCheckToken = webServer.checkToken("Bearer $token").execute()
        assertEquals(responseCheckToken.code(), SERVER_RESPONSE_TOKEN_OK)
    }

    @Test
    fun testRegistrationOk() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val response = webServer.register("Bearer $token", RegisterUser(true, newEmail, newUserName,
                                                                        password)).execute()
        val responseBody = response.body()
        assertNotNull(responseBody)
        assertNotNull(responseBody?.token)
        assertEquals(response.code(), SERVER_RESPONSE_REGISTER_OK)

        deleteUser(responseBody?.token)
    }

    @Test
    fun testRegisterWithNewUserButExistingEmail() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val response = webServer.register("Bearer $token", RegisterUser(true, email, newUserName,
                                                                        password)
        ).execute()

        assertEquals(response.code(), SERVER_RESPONSE_REGISTER_UNPROCESSABLE_ENTITY)

        assertNotNull(response.errorBody())
        val errorBody = response.errorBody()?.string()
        assertNotNull(parseRegisterErrorMessage(errorBody)?.email)
    }

    @Test
    fun testRegisterWithExistingUserButNewEmail() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val response = webServer.register("Bearer $token", RegisterUser(true, newEmail, username,
                                                                        password)).execute()

        assertEquals(response.code(), SERVER_RESPONSE_REGISTER_UNPROCESSABLE_ENTITY)

        assertNotNull(response.errorBody())
        val errorBody = response.errorBody()?.string()
        assertNotNull(parseRegisterErrorMessage(errorBody)?.username)
    }

    @Test
    fun testRegisterAndLogin() {
        val token = sharedPreferences.getString(Constants.TOKEN, Constants.NO_TOKEN)
        val registrationResponse = webServer.register(
            "Bearer $token", RegisterUser(
                true, newEmail,
                newUserName,
                password
            )
        ).execute()

        val loginResponse = webServer.login("Bearer $token", LoginUser(newUserName, password))
            .execute()

        assertEquals(registrationResponse.code(), SERVER_RESPONSE_REGISTER_OK)
        assertEquals(loginResponse.code(), SERVER_RESPONSE_TOKEN_OK)

        deleteUser(loginResponse.body()?.token)
    }

    @Test
    fun testUpgradeExpiredToken() {
        val token = "ee447d8d9013f72ba8f170a48efbedbf"
        val upgradeResponse = webServer.upgradeToken(DeprecatedToken(token)).execute()
        assertEquals(upgradeResponse.code(), SERVER_RESPONSE_INVALID_UPLOAD_TOKEN)
    }

    private fun parseRegisterErrorMessage(errorBody: String?) =
        Moshi.Builder().build().adapter<RegisterFailedResponse>(
            RegisterFailedResponse::class
                .java
        ).fromJson(errorBody)

    private fun deleteUser(token: String?) {
        val response = webServer.deleteUser("Bearer $token").execute()
        assertEquals(response.code(), 204)
    }
}