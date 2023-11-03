package com.afkanerd.deku.DefaultSMS.Fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afkanerd.deku.DefaultSMS.Models.Archive.ArchiveHandler;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversationsViewModel;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversations;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversationsDao;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ViewHolders.TemplateViewHolder;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ConversationsThreadRecyclerAdapter;
import com.afkanerd.deku.DefaultSMS.R;

import java.util.List;
import java.util.Set;

public class ThreadedConversationsFragment extends Fragment {
    BroadcastReceiver incomingBroadcastReceiver;
    BroadcastReceiver incomingDataBroadcastReceiver;

    ThreadedConversationsViewModel threadedConversationsViewModel;
    ConversationsThreadRecyclerAdapter conversationsThreadRecyclerAdapter;
    RecyclerView messagesThreadRecyclerView;

    ArchiveHandler archiveHandler;

    Toolbar toolbar;

    Handler mHandler = new Handler();

    public static final String MESSAGES_THREAD_FRAGMENT_TYPE = "MESSAGES_THREAD_FRAGMENT_TYPE";
    public static final String ALL_MESSAGES_THREAD_FRAGMENT = "ALL_MESSAGES_THREAD_FRAGMENT";
    public static final String PLAIN_MESSAGES_THREAD_FRAGMENT = "PLAIN_MESSAGES_THREAD_FRAGMENT";
    public static final String ENCRYPTED_MESSAGES_THREAD_FRAGMENT = "ENCRYPTED_MESSAGES_THREAD_FRAGMENT";

    public static final String AUTOMATED_MESSAGES_THREAD_FRAGMENT = "AUTOMATED_MESSAGES_THREAD_FRAGMENT";

    private OnViewManipulationListener mListener;
    ThreadedConversationsDao threadedConversationsDao;

    public interface OnViewManipulationListener extends HomepageFragment.TabListenerInterface {
        void activateDefaultToolbar();
        void deactivateDefaultToolbar(int size);

        void setRecyclerViewAdapter(String name, ConversationsThreadRecyclerAdapter conversationsThreadRecyclerAdapter);
        void setViewModel(String name, ThreadedConversationsViewModel threadedConversationsViewModel);
        Toolbar getToolbar();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages_threads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String messageType = args.getString(MESSAGES_THREAD_FRAGMENT_TYPE);

        archiveHandler = new ArchiveHandler(getContext());

        toolbar = mListener.getToolbar();

        threadedConversationsViewModel = new ViewModelProvider(this).get(
                ThreadedConversationsViewModel.class);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false);
        conversationsThreadRecyclerAdapter = new ConversationsThreadRecyclerAdapter( getContext());
        conversationsThreadRecyclerAdapter.setHasStableIds(true);
        mListener.setRecyclerViewAdapter(messageType, conversationsThreadRecyclerAdapter);
        mListener.setViewModel(messageType, threadedConversationsViewModel);

        messagesThreadRecyclerView = view.findViewById(R.id.messages_threads_recycler_view);
        messagesThreadRecyclerView.setLayoutManager(linearLayoutManager);
        messagesThreadRecyclerView.setAdapter(conversationsThreadRecyclerAdapter);
        messagesThreadRecyclerView.setItemViewCacheSize(20);
        messagesThreadRecyclerView.setDrawingCacheEnabled(true);
        messagesThreadRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        threadedConversationsDao = ThreadedConversations.getDao(getContext());
        try {
            threadedConversationsViewModel.get(threadedConversationsDao, getContext()).observe(getViewLifecycleOwner(),
                    new Observer<List<ThreadedConversations>>() {
                        @Override
                        public void onChanged(List<ThreadedConversations> smsList) {
                            TextView textView = view.findViewById(R.id.homepage_no_message);
                            if(smsList == null || smsList.isEmpty()) {
                                textView.setVisibility(View.VISIBLE);
                            }
                            else {
                                textView.setVisibility(View.GONE);
                            }
                            view.findViewById(R.id.homepage_messages_loader).setVisibility(View.GONE);
                            conversationsThreadRecyclerAdapter.mDiffer.submitList(smsList);
                        }
                    });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        conversationsThreadRecyclerAdapter.selectedItems.observe(getViewLifecycleOwner(),
                new Observer<Set<TemplateViewHolder>>() {
                    @Override
                    public void onChanged(Set<TemplateViewHolder> stringViewHolderHashMap) {
                        highlightListener(stringViewHolderHashMap.size(), view);
                    }
                });
//        setRefreshTimer();
    }


    private void highlightListener(int size, View view){
        Menu menu = toolbar.getMenu();
        if(size < 1) {
            menu.setGroupVisible(R.id.threads_menu, false);
            mListener.activateDefaultToolbar();
        } else {
            mListener.deactivateDefaultToolbar(size);
            menu.setGroupVisible(R.id.threads_menu, true);
        }
    }

    private void setRefreshTimer() {
        final int recyclerViewTimeUpdateLimit = 60 * 1000;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(conversationsThreadRecyclerAdapter.selectedItems.getValue()==null ||
                        conversationsThreadRecyclerAdapter.selectedItems.getValue().isEmpty())
                    conversationsThreadRecyclerAdapter.notifyDataSetChanged();
                mHandler.postDelayed(this, recyclerViewTimeUpdateLimit);
            }
        }, recyclerViewTimeUpdateLimit);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof OnViewManipulationListener) {
            mListener = (OnViewManipulationListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnViewManipulationListener");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (archiveHandler != null)
            archiveHandler.close();
    }
}