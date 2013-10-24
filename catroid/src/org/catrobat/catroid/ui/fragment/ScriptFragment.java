/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ui.fragment;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ListView;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.bricks.AllowedAfterDeadEndBrick;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.content.bricks.DeadEndBrick;
import org.catrobat.catroid.content.bricks.NestingBrick;
import org.catrobat.catroid.content.bricks.ScriptBrick;
import org.catrobat.catroid.ui.BottomBar;
import org.catrobat.catroid.ui.ScriptActivity;
import org.catrobat.catroid.ui.ViewSwitchLock;
import org.catrobat.catroid.ui.adapter.BrickAdapter;
import org.catrobat.catroid.ui.adapter.BrickAdapter.OnBrickEditListener;
import org.catrobat.catroid.ui.dialogs.CustomAlertDialogBuilder;
import org.catrobat.catroid.ui.dialogs.DeleteLookDialog;
import org.catrobat.catroid.ui.dragndrop.DragAndDropListView;
import org.catrobat.catroid.ui.fragment.BrickCategoryFragment.OnCategorySelectedListener;
import org.catrobat.catroid.utils.Utils;

import java.util.List;
import java.util.concurrent.locks.Lock;

public class ScriptFragment extends ScriptActivityFragment implements OnCategorySelectedListener, OnBrickEditListener {

	public static final String TAG = ScriptFragment.class.getSimpleName();

	private static String actionModeTitle;

	private static String singleItemAppendixActionMode;
	private static String multipleItemAppendixActionMode;

	private static int selectedBrickPosition = Constants.NO_POSITION;

	private ActionMode actionMode;

	private BrickAdapter adapter;
	private DragAndDropListView listView;

	private Sprite sprite;
	private Script scriptToEdit;

	private BrickListChangedReceiver brickListChangedReceiver;

	private Lock viewSwitchLock = new ViewSwitchLock();

	private boolean deleteScriptFromContextMenu = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_script, null);

		listView = (DragAndDropListView) rootView.findViewById(android.R.id.list);

		return rootView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		initListeners();
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		menu.findItem(R.id.show_details).setVisible(false);
		menu.findItem(R.id.rename).setVisible(false);
		menu.findItem(R.id.backpack).setVisible(false);
		menu.findItem(R.id.unpacking).setVisible(false);

		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.findItem(R.id.delete).setVisible(true);
		menu.findItem(R.id.copy).setVisible(true);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onStart() {
		super.onStart();
		sprite = ProjectManager.getInstance().getCurrentSprite();
		if (sprite == null) {
			return;
		}

		initListeners();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!Utils.checkForExternalStorageAvailableAndDisplayErrorIfNot(getActivity())) {
			return;
		}

		if (brickListChangedReceiver == null) {
			brickListChangedReceiver = new BrickListChangedReceiver();
		}

		IntentFilter filterBrickListChanged = new IntentFilter(ScriptActivity.ACTION_BRICK_LIST_CHANGED);
		getActivity().registerReceiver(brickListChangedReceiver, filterBrickListChanged);

		initListeners();
	}

	@Override
	public void onPause() {
		super.onPause();
		ProjectManager projectManager = ProjectManager.getInstance();

		if (brickListChangedReceiver != null) {
			getActivity().unregisterReceiver(brickListChangedReceiver);
		}
		if (projectManager.getCurrentProject() != null) {
			projectManager.saveProject();
			projectManager.getCurrentProject().removeUnusedBroadcastMessages(); // TODO: Find better place
		}
	}

	public BrickAdapter getAdapter() {
		return adapter;
	}

	@Override
	public DragAndDropListView getListView() {
		return listView;
	}

	@Override
	public void onCategorySelected(String category) {
		AddBrickFragment addBrickFragment = AddBrickFragment.newInstance(category, this);
		FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.add(R.id.script_fragment_container, addBrickFragment,
				AddBrickFragment.ADD_BRICK_FRAGMENT_TAG);
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
		adapter.notifyDataSetChanged();

	}

	public void updateAdapterAfterAddNewBrick(Brick brickToBeAdded) {
		int firstVisibleBrick = listView.getFirstVisiblePosition();
		int lastVisibleBrick = listView.getLastVisiblePosition();
		int position = ((1 + lastVisibleBrick - firstVisibleBrick) / 2);
		position += firstVisibleBrick;
		adapter.addNewBrick(position, brickToBeAdded, true);
		adapter.notifyDataSetChanged();
	}

	private void initListeners() {
		sprite = ProjectManager.getInstance().getCurrentSprite();
		if (sprite == null) {
			return;
		}

		getSherlockActivity().findViewById(R.id.button_add).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				handleAddButton();
			}
		});

		adapter = new BrickAdapter(getActivity(), sprite, listView);
		adapter.setOnBrickEditListener(this);

		if (ProjectManager.getInstance().getCurrentSprite().getNumberOfScripts() > 0) {
			ProjectManager.getInstance().setCurrentScript(((ScriptBrick) adapter.getItem(0)).initScript(sprite));
		}

		listView.setOnCreateContextMenuListener(this);
		listView.setOnDragAndDropListener(adapter);
		listView.setAdapter(adapter);
		registerForContextMenu(listView);
	}

	private void showCategoryFragment() {
		BrickCategoryFragment brickCategoryFragment = new BrickCategoryFragment();
		brickCategoryFragment.setOnCategorySelectedListener(this);
		FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

		fragmentTransaction.add(R.id.script_fragment_container, brickCategoryFragment,
				BrickCategoryFragment.BRICK_CATEGORY_FRAGMENT_TAG);

		fragmentTransaction.addToBackStack(BrickCategoryFragment.BRICK_CATEGORY_FRAGMENT_TAG);
		fragmentTransaction.commit();

		adapter.notifyDataSetChanged();
	}

	@Override
	public boolean getShowDetails() {
		//Currently no showDetails option
		return false;
	}

	@Override
	public void setShowDetails(boolean showDetails) {
		//Currently no showDetails option
	}

	@Override
	protected void showRenameDialog() {
		//Rename not supported
	}

	@Override
	public void startRenameActionMode() {
		//Rename not supported
	}

	@Override
	public void startCopyActionMode() {
		startActionMode(copyModeCallBack);
	}

	@Override
	public void startDeleteActionMode() {
		startActionMode(deleteModeCallBack);
	}

	private void startActionMode(ActionMode.Callback actionModeCallback) {
		if (actionMode == null) {
			actionMode = getSherlockActivity().startActionMode(actionModeCallback);

			for (int i = adapter.listItemCount; i < adapter.getBrickList().size(); i++) {
				adapter.getView(i, null, getListView());
			}

			unregisterForContextMenu(listView);
			BottomBar.hideBottomBar(getActivity());
			adapter.setCheckboxVisibility(View.VISIBLE);
			adapter.setActionMode(true);
		}
	}

	@Override
	public void handleAddButton() {
		if (!viewSwitchLock.tryLock()) {
			return;
		}

		if (listView.isCurrentlyDragging()) {
			listView.animateHoveringBrick();
			return;
		}

		showCategoryFragment();
	}

	@Override
	public boolean getActionModeActive() {
		return actionModeActive;
	}

	@Override
	public int getSelectMode() {
		return adapter.getSelectMode();
	}

	@Override
	public void setSelectMode(int selectMode) {
		adapter.setSelectMode(selectMode);
		adapter.notifyDataSetChanged();
	}

	@Override
	protected void showDeleteDialog() {

		DeleteLookDialog deleteLookDialog = DeleteLookDialog.newInstance(selectedBrickPosition);
		deleteLookDialog.show(getFragmentManager(), DeleteLookDialog.DIALOG_FRAGMENT_TAG);
	}

	private class BrickListChangedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ScriptActivity.ACTION_BRICK_LIST_CHANGED)) {
				adapter.updateProjectBrickList();
			}
		}
	}

	private void addSelectAllActionModeButton(ActionMode mode, Menu menu) {
		Utils.addSelectAllActionModeButton(getLayoutInflater(null), mode, menu).setOnClickListener(
				new OnClickListener() {

					@Override
					public void onClick(View view) {
						adapter.checkAllItems();
						view.setVisibility(View.GONE);
					}

				});
	}

	private ActionMode.Callback deleteModeCallBack = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			setSelectMode(ListView.CHOICE_MODE_MULTIPLE);
			setActionModeActive(true);

			actionModeTitle = getString(R.string.delete);
			singleItemAppendixActionMode = getString(R.string.brick_single);
			multipleItemAppendixActionMode = getString(R.string.brick_multiple);

			mode.setTitle(actionModeTitle);
			addSelectAllActionModeButton(mode, menu);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {

			if (adapter.getAmountOfCheckedItems() == 0) {
				clearCheckedBricksAndEnableButtons();
			} else {
				showConfirmDeleteDialog(false);
			}
		}
	};

	private ActionMode.Callback copyModeCallBack = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {

			setSelectMode(ListView.CHOICE_MODE_MULTIPLE);
			setActionModeActive(true);

			actionModeTitle = getString(R.string.copy);
			singleItemAppendixActionMode = getString(R.string.brick_single);
			multipleItemAppendixActionMode = getString(R.string.brick_multiple);

			mode.setTitle(actionModeTitle);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {

			List<Brick> checkedBricks = adapter.getCheckedBricks();

			for (Brick brick : checkedBricks) {
				copyBrick(brick);
				if (brick instanceof ScriptBrick) {
					break;
				}
			}
			setSelectMode(ListView.CHOICE_MODE_NONE);
			adapter.clearCheckedItems();

			actionMode = null;
			setActionModeActive(false);

			registerForContextMenu(listView);
			BottomBar.showBottomBar(getActivity());
			adapter.setActionMode(false);

		}
	};

	private void copyBrick(Brick brick) {
		if (brick instanceof NestingBrick
				&& (brick instanceof AllowedAfterDeadEndBrick || brick instanceof DeadEndBrick)) {
			return;
		}

		if (brick instanceof ScriptBrick) {
			scriptToEdit = ((ScriptBrick) brick).initScript(ProjectManager.getInstance().getCurrentSprite());

			Script clonedScript = scriptToEdit.copyScriptForSprite(sprite);

			sprite.addScript(clonedScript);
			adapter.initBrickList();
			adapter.notifyDataSetChanged();

			return;
		}

		int brickId = adapter.getBrickList().indexOf(brick);
		if (brickId == -1) {
			return;
		}

		int newPosition = adapter.getCount();
		Brick copy = brick.clone();
		Script scriptList = ProjectManager.getInstance().getCurrentScript();

		if (brick instanceof NestingBrick) {
			NestingBrick nestingBrickCopy = (NestingBrick) copy;
			nestingBrickCopy.initialize();

			for (Brick nestingBrick : nestingBrickCopy.getAllNestingBrickParts(true)) {
				scriptList.addBrick(nestingBrick);
			}
		} else {
			scriptList.addBrick(copy);
		}

		adapter.addNewBrick(newPosition, copy, false);
		adapter.initBrickList();

		ProjectManager.getInstance().saveProject();
		adapter.notifyDataSetChanged();
	}

	private void deleteBrick(Brick brick) {

		if (brick instanceof ScriptBrick) {
			scriptToEdit = ((ScriptBrick) brick).initScript(ProjectManager.getInstance().getCurrentSprite());
			adapter.handleScriptDelete(sprite, scriptToEdit);
			return;
		}
		int brickId = adapter.getBrickList().indexOf(brick);
		if (brickId == -1) {
			return;
		}
		adapter.removeFromBrickListAndProject(brickId, true);
	}

	private void deleteCheckedBricks() {
		List<Brick> checkedBricks = adapter.getReversedCheckedBrickList();

		for (Brick brick : checkedBricks) {
			deleteBrick(brick);
		}
	}

	private void showConfirmDeleteDialog(boolean fromContextMenu) {
		this.deleteScriptFromContextMenu = fromContextMenu;
		int titleId;
		if ((deleteScriptFromContextMenu && scriptToEdit.getBrickList().size() == 0)
				|| adapter.getAmountOfCheckedItems() == 1) {
			titleId = R.string.dialog_confirm_delete_brick_title;
		} else {
			titleId = R.string.dialog_confirm_delete_multiple_bricks_title;
		}

		AlertDialog.Builder builder = new CustomAlertDialogBuilder(getActivity());
		builder.setTitle(titleId);
		builder.setMessage(R.string.dialog_confirm_delete_brick_message);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				if (deleteScriptFromContextMenu) {
					adapter.handleScriptDelete(sprite, scriptToEdit);
				} else {
					deleteCheckedBricks();
					clearCheckedBricksAndEnableButtons();
				}
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				if (!deleteScriptFromContextMenu) {
					clearCheckedBricksAndEnableButtons();
				}
			}
		});

		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	private void clearCheckedBricksAndEnableButtons() {
		setSelectMode(ListView.CHOICE_MODE_NONE);
		adapter.clearCheckedItems();

		actionMode = null;
		setActionModeActive(false);

		registerForContextMenu(listView);
		BottomBar.showBottomBar(getActivity());
		adapter.setActionMode(false);
	}

	@Override
	public void onBrickEdit(View view) {

	}

	@Override
	public void onBrickChecked() {

		if (actionMode == null) {
			return;
		}

		int numberOfSelectedItems = adapter.getAmountOfCheckedItems();

		if (numberOfSelectedItems == 0) {
			actionMode.setTitle(actionModeTitle);
		} else {
			String appendix = multipleItemAppendixActionMode;

			if (numberOfSelectedItems == 1) {
				appendix = singleItemAppendixActionMode;
			}

			String numberOfItems = Integer.toString(numberOfSelectedItems);
			String completeTitle = actionModeTitle + " " + numberOfItems + " " + appendix;

			int titleLength = actionModeTitle.length();

			Spannable completeSpannedTitle = new SpannableString(completeTitle);
			completeSpannedTitle.setSpan(
					new ForegroundColorSpan(getResources().getColor(R.color.actionbar_title_color)), titleLength + 1,
					titleLength + (1 + numberOfItems.length()), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			actionMode.setTitle(completeSpannedTitle);
		}
	}

	@Override
	public void startBackPackActionMode() {

	}

}
