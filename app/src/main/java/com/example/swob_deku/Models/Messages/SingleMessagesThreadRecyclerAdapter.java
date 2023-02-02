package com.example.swob_deku.Models.Messages;

import android.content.Context;
import android.provider.Telephony;
import android.text.Spannable;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swob_deku.BuildConfig;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.R;
import com.google.android.material.card.MaterialCardView;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

public class SingleMessagesThreadRecyclerAdapter extends RecyclerView.Adapter{

    Context context;
    int renderLayoutReceived, renderLayoutSent, renderLayoutTimestamp;
    Long focusId;
    RecyclerView view;
    String searchString;

    private final AsyncListDiffer<SMS> mDiffer = new AsyncListDiffer(this, DIFF_CALLBACK);

    final int MESSAGE_TYPE_ALL = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL;
    final int MESSAGE_TYPE_INBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
    final int MESSAGE_TYPE_SENT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;
    final int MESSAGE_TYPE_DRAFT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT;
    final int MESSAGE_TYPE_OUTBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX;
    final int MESSAGE_TYPE_FAILED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED;
    final int MESSAGE_TYPE_QUEUED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED;

    public SingleMessagesThreadRecyclerAdapter(Context context, int renderLayoutReceived,
                                               int renderLayoutSent,
                                               int renderLayoutTimestamp,
                                               Long focusId,
                                               String searchString,
                                               RecyclerView view) {
        this.context = context;
        this.renderLayoutReceived = renderLayoutReceived;
        this.renderLayoutSent = renderLayoutSent;
        this.renderLayoutTimestamp = renderLayoutTimestamp;
        this.focusId = focusId;
        this.searchString = searchString;
        this.view = view;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(this.context);

        switch(viewType) {
            // https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns#MESSAGE_TYPE_OUTBOX
            case 100: {
                View view = inflater.inflate(this.renderLayoutTimestamp, parent, false);
                return new MessageTimestampViewerHandler(view);
            }
            case MESSAGE_TYPE_INBOX: {
                View view = inflater.inflate(this.renderLayoutReceived, parent, false);
                return new MessageReceivedViewHandler(view);
            }
            case MESSAGE_TYPE_QUEUED:
            case MESSAGE_TYPE_FAILED:
            case MESSAGE_TYPE_OUTBOX:
            case MESSAGE_TYPE_SENT: {
                View view = inflater.inflate(this.renderLayoutSent, parent, false);
                return new MessageSentViewHandler(view);
            }
        }

        return null;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        for(int i=0;i<mDiffer.getCurrentList().size();++i) {
            SMS sms = mDiffer.getCurrentList().get(i);
            if (focusId!=null
                    && searchString!=null
                    && sms.id.equals(Long.toString(focusId))
                    && !searchString.isEmpty()) {
                String text = sms.getBody();
                Spannable spannable = Spannable.Factory.getInstance().newSpannable(text);

                for (int index = text.indexOf(searchString); index >= 0; index = text.indexOf(searchString, index + 1)) {
                    spannable.setSpan(new BackgroundColorSpan(context.getResources().getColor(R.color.highlight_yellow)),
                            index, index + (searchString.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.black)),
                            index, index + (searchString.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                // TODO: not working
//                switch (holder.getItemViewType()) {
//                    case 1: {
//                        ((MessageReceivedViewHandler) holder).receivedMessage.setText(spannable);
//                        break;
//                    }
//                    case 5:
//                    case 4:
//                    case 2: {
//                        ((MessageSentViewHandler) holder).sentMessage.setText(spannable);
//                        break;
//                    }
//                }
//                break;
                this.view.smoothScrollToPosition(i);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final SMS sms = mDiffer.getCurrentList().get(position);

        // TODO: for search
//        if(focusId != -1 && sms.getId() != null && Long.valueOf(sms.getId()) == focusId) {
//            final int finalPosition = position;
//            this.focusPosition = finalPosition;
//        }

        String date = sms.getDate();
        if(sms.isDatesOnly()) {
            if (DateUtils.isToday(Long.parseLong(date))) {
                DateFormat dateFormat = new SimpleDateFormat("h:mm a");
                date = "Today " + dateFormat.format(new Date(Long.parseLong(date)));
            }
            else {
                DateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d h:mm a");
                date = dateFormat.format(new Date(Long.parseLong(date)));
            }
            ((MessageTimestampViewerHandler)holder).date.setText(date);
            return;
        }

        if (DateUtils.isToday(Long.parseLong(date))) {
            DateFormat dateFormat = new SimpleDateFormat("h:mm a");
            date = "Today " + dateFormat.format(new Date(Long.parseLong(date)));
        }
        else {
            DateFormat dateFormat = new SimpleDateFormat("EE h:mm a");
            date = dateFormat.format(new Date(Long.parseLong(date)));
        }

        switch(sms.getType()) {
//            https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns?hl=en#TYPE
            case MESSAGE_TYPE_INBOX:
                TextView receivedMessage = ((MessageReceivedViewHandler)holder).receivedMessage;
                receivedMessage.setText(sms.getBody());

                TextView dateView = ((MessageReceivedViewHandler)holder).date;
                dateView.setVisibility(View.INVISIBLE);
                dateView.setText(date);

                ((MessageReceivedViewHandler)holder).receivedMessage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(((MessageReceivedViewHandler)holder).date.getVisibility() == View.VISIBLE) {
                            dateView.setVisibility(View.INVISIBLE);
                        }
                        else {
                            dateView.setVisibility(View.VISIBLE);
                        }
                    }
                });
                break;

            case MESSAGE_TYPE_SENT:
                ((MessageSentViewHandler) holder).sentMessage.setText(sms.getBody());
                ((MessageSentViewHandler) holder).date.setText(date);
                ((MessageSentViewHandler) holder).date.setVisibility(View.INVISIBLE);

                final int status = sms.getStatusCode();
                String statusMessage = status == Telephony.TextBasedSmsColumns.STATUS_COMPLETE ?
                        "delivered" : "sent";
                statusMessage = "• " + statusMessage;

                ((MessageSentViewHandler) holder).sentMessageStatus.setVisibility(View.INVISIBLE);
                ((MessageSentViewHandler) holder).sentMessageStatus.setText(statusMessage);

                ((MessageSentViewHandler) holder).sentMessage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(((MessageSentViewHandler)holder).date.getVisibility() == View.VISIBLE) {
                            ((MessageSentViewHandler)holder).date.setVisibility(View.INVISIBLE);
                            ((MessageSentViewHandler)holder).sentMessageStatus.setVisibility(View.INVISIBLE);
                        }
                        else {
                            ((MessageSentViewHandler)holder).date.setVisibility(View.VISIBLE);
                            ((MessageSentViewHandler)holder).sentMessageStatus.setVisibility(View.VISIBLE);
                        }
                    }
                });

                break;
            case MESSAGE_TYPE_OUTBOX:
                ((MessageSentViewHandler)holder).sentMessage.setText(mDiffer.getCurrentList().get(position).getBody());
                ((MessageSentViewHandler) holder).date.setText(date);
                ((MessageSentViewHandler) holder).sentMessageStatus.setText("• sending...");
                break;
            case MESSAGE_TYPE_FAILED:
                ((MessageSentViewHandler)holder).sentMessage.setText(mDiffer.getCurrentList().get(position).getBody());
                ((MessageSentViewHandler) holder).date.setText(date);
                ((MessageSentViewHandler) holder).sentMessageStatus.setText("• failed");
                break;
        }
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    public void submitList(List<SMS> list) {
        mDiffer.submitList(list);
    }

    @Override
    public int getItemViewType(int position) {
        if(mDiffer.getCurrentList().get(position).isDatesOnly())
            return 100;

        int messageType = mDiffer.getCurrentList().get(position).getType();
        return (messageType > -1 )? messageType : 0;
    }

    public class MessageTimestampViewerHandler extends RecyclerView.ViewHolder {
        TextView date;
        public MessageTimestampViewerHandler(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.messages_thread_timestamp_textview);
        }
    }

    public class MessageSentViewHandler extends RecyclerView.ViewHolder {
        TextView sentMessage;
        TextView sentMessageStatus;
        TextView date;
        MaterialCardView layout;
        public MessageSentViewHandler(@NonNull View itemView) {
            super(itemView);
            sentMessage = itemView.findViewById(R.id.message_thread_sent_card_text);
            sentMessageStatus = itemView.findViewById(R.id.message_thread_sent_status_text);
            date = itemView.findViewById(R.id.message_thread_sent_date_text);
            layout = itemView.findViewById(R.id.text_sent_container);
        }
    }

    public class MessageReceivedViewHandler extends RecyclerView.ViewHolder {
        TextView receivedMessage;
        TextView date;
        public MessageReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
            receivedMessage = itemView.findViewById(R.id.message_thread_received_card_text);
            date = itemView.findViewById(R.id.message_thread_received_date_text);
        }
    }

    public static final DiffUtil.ItemCallback<SMS> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SMS>() {
                @Override
                public boolean areItemsTheSame(@NonNull SMS oldItem, @NonNull SMS newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull SMS oldItem, @NonNull SMS newItem) {
                    return oldItem.equals(newItem);
                }
            };
}
