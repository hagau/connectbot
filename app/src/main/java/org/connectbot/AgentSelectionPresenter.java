package org.connectbot;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class AgentSelectionPresenter {
	private AgentSelectionDialog mDialog;
	private List<String> mAgentList;
	private List<String> mAgentNameList;
	private int mSelection;
	private HostEditorFragment mHostEditorFragment;

	public AgentSelectionPresenter(List<String> agentList, List<String> agentNameList, HostEditorFragment hostEditorFragment) {
		mDialog = AgentSelectionDialog.newInstance(agentNameList, this);
		mAgentList = agentList;
		mAgentNameList = agentNameList;
		mHostEditorFragment = hostEditorFragment;
	}

	public void show() {
		mDialog.show(mHostEditorFragment.getFragmentManager(), "agentSelectionDialog");
	}

	private void selectAgent(int position) {
		mSelection = position;
		Log.d("====>>>", mAgentList.get(position));
	}
	private void returnAgent() {
		mHostEditorFragment.onAgentSelected(mAgentList.get(mSelection));
		mDialog.dismiss();
	}
	private void cancel() {
		mHostEditorFragment.onAgentSelected(null);
		mDialog.dismiss();
	}

	public static class AgentSelectionDialog extends DialogFragment {
		private static final String AGENTLIST = "agentList";
		private Button mSelect;
		private Button mCancel;
		private RecyclerView mAgentRecyclerView;
		private AgentSelectionPresenter mPresenter;

		public static AgentSelectionDialog newInstance(List<String> agentList, AgentSelectionPresenter presenter) {

			Bundle args = new Bundle();
			args.putStringArrayList(AGENTLIST, (ArrayList<String>) agentList);

			AgentSelectionDialog fragment = new AgentSelectionDialog();
			fragment.setPresenter(presenter);
			fragment.setArguments(args);
			return fragment;
		}

		private void setPresenter(AgentSelectionPresenter presenter) {
			mPresenter = presenter;
		}

		@Override
		public void onCreate(@Nullable Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
		}

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
			Activity activity = getActivity();
			View view = inflater.inflate(R.layout.agent_selection_dialog, container, false);

			mSelect = (Button) view.findViewById(R.id.button_select);
			mCancel = (Button) view.findViewById(R.id.button_cancel);

			mAgentRecyclerView = (RecyclerView) view.findViewById(R.id.agent_recycler);
			mAgentRecyclerView.setLayoutManager(new LinearLayoutManager(activity));

			ArrayList<String> agentList = getArguments().getStringArrayList(AGENTLIST);
			AgentAdapter agentAdapter = new AgentAdapter(agentList);
			mAgentRecyclerView.setAdapter(agentAdapter);

			mCancel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mPresenter.cancel();
				}
			});
			mSelect.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mPresenter.returnAgent();
				}
			});

			mAgentRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
				GestureDetector mTapDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onSingleTapUp(MotionEvent e) {
						return true;
					}
				});

				@Override
				public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
					View itemView = view.findChildViewUnder(e.getX(), e.getY());
					if (itemView != null && mTapDetector.onTouchEvent(e)) {
						mPresenter.selectAgent(view.getChildAdapterPosition(itemView));
						return true;
					}
					return false;
				}
			});


			return view;
		}
	}

	public static class AgentAdapter extends RecyclerView.Adapter<AgentViewHolder> {
		private List<String> agentList;

		public AgentAdapter(List<String> agentList) {
			this.agentList = agentList;
		}

		@Override
		public AgentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			final View agentItemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.agent_selection_item, parent, false);
			final AgentViewHolder viewHolder = new AgentViewHolder(agentItemView);

			return viewHolder;
		}

		@Override
		public void onBindViewHolder(AgentViewHolder holder, int position) {
			holder.vName.setText(agentList.get(position));
		}

		@Override
		public int getItemCount() {
			return agentList.size();
		}
	}

	private static class AgentViewHolder extends RecyclerView.ViewHolder {
		public final TextView vName;

		AgentViewHolder(View itemView) {
			super(itemView);

			vName = (TextView) itemView.findViewById(R.id.agentName);
		}

	}
}
