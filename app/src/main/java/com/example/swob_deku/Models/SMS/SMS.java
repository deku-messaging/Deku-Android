package com.example.swob_deku.Models.SMS;

import static com.example.swob_deku.Commons.Helpers.getUserCountry;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.example.swob_deku.Commons.Helpers;
import com.example.swob_deku.Models.Contacts.Contacts;
import com.example.swob_deku.Models.Security.SecurityECDH;
import com.example.swob_deku.Models.Security.SecurityHelpers;
import com.google.i18n.phonenumbers.NumberParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMS {
    // https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns#constants_1

    String body = new String();
    public String displayName = "";

    public int displayColor = -1;

    public boolean isContact = false;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    String address = new String();
    String threadId = "-1";
    String date = new String();
    int type;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int messageCount = -1;

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    String errorCode;
    int statusCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int isRead() {
        return read;
    }

    public void setRead(int read) {
        this.read = read;
    }

    public String id = "";

    public int read;

    public String routerStatus = new String();

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(int displayColor) {
        this.displayColor = displayColor;
    }

    public boolean isContact() {
        return isContact;
    }

    public void setContact(boolean contact) {
        isContact = contact;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getRead() {
        return read;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Boolean getDatesOnly() {
        return datesOnly;
    }

    public void setDatesOnly(Boolean datesOnly) {
        this.datesOnly = datesOnly;
    }

    public ArrayList<String> getRoutingUrls() {
        return routingUrls;
    }

    public String subscriptionId = new String();

    public Boolean datesOnly = false;

    public Boolean isDatesOnly() {
        return this.datesOnly;
    }

    public String getRouterStatus() {
        return this.routerStatus;
    }

    public void setRouterStatus(String routerStatus) {
        this.routerStatus = routerStatus;
    }

    public ArrayList<String> routingUrls = new ArrayList<>();

    public void setRoutingUrls(ArrayList<String> routingUrls) {
        this.routingUrls = routingUrls;
    }

    public void addRoutingUrl(String routingUrl) {
        this.routingUrls.add(routingUrl);
    }

    public SMS(String dates) {
        this.date = dates;
        this.datesOnly = true;
        this.type = 100;
    }

    // https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns
    public SMS(Cursor cursor) {
        int bodyIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY);
        int addressIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.ADDRESS);
        int threadIdIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.THREAD_ID);
        int dateIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.DATE);
        int typeIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.TYPE);
        int errorCodeIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.ERROR_CODE);
        int statusCodeIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.STATUS);
        int readIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.READ);
        int idIndex = cursor.getColumnIndex(Telephony.Sms._ID);
        int subscriptionIdIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID);

        this.type =  cursor.getInt(typeIndex);
        this.body = String.valueOf(cursor.getString(bodyIndex));
        this.address = String.valueOf(cursor.getString(addressIndex));
        this.threadId = cursor.getString(threadIdIndex);
        this.date = String.valueOf(cursor.getString(dateIndex));

        if(subscriptionIdIndex > -1)
            this.subscriptionId = String.valueOf(cursor.getString(subscriptionIdIndex));

        if(idIndex > -1 ) {
            this.id = String.valueOf(cursor.getString(idIndex));
        }

        if(readIndex > -1 ) {
            this.read = cursor.getInt(readIndex);
        }

        if(threadIdIndex > -1 )
            this.threadId = String.valueOf(cursor.getString(threadIdIndex));

        if(errorCodeIndex > -1 )
            this.errorCode = String.valueOf(cursor.getString(errorCodeIndex));
        if(statusCodeIndex > -1 )
            this.statusCode = cursor.getInt(statusCodeIndex);

    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof SMS) {
            SMS sms = (SMS) obj;

            return sms.getId().equals(this.id) &&
                    sms.threadId.equals(this.threadId) &&
                    sms.address.equals(this.address) &&
                    sms.body.equals(this.body) &&
                    sms.statusCode == this.statusCode &&
                    sms.read == this.read &&
                    sms.date.equals(this.date);
        }
        return false;
    }

    public static final DiffUtil.ItemCallback<SMS> DIFF_CALLBACK = new DiffUtil.ItemCallback<SMS>() {
        @Override
        public boolean areItemsTheSame(@NonNull SMS oldItem, @NonNull SMS newItem) {
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull SMS oldItem, @NonNull SMS newItem) {
            return oldItem.equals(newItem);
        }
    };

    public static class SMSMetaEntity {
        public static final String THREAD_ID = "THREAD_ID";
        public static final String ADDRESS = "ADDRESS";

        public static final String SHARED_SMS_BODY = "sms_body";

        public enum ENCRYPTION_STATE {
            NOT_ENCRYPTED,
            SENT_PENDING_AGREEMENT,
            RECEIVED_PENDING_AGREEMENT,

            RECEIVED_AGREEMENT_REQUEST,
            ENCRYPTED
        }

        private String address, threadId;
        private String _address;

        public void setThreadId(String threadId) {
            this.threadId = threadId;
        }

        public void setAddress(Context context, String address) {
            this.address = address;
            try {
                this._address = formatPhoneNumbers(context, this.address);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this._address = this.address;
        }

        public String getThreadId() {
            return this.threadId;
        }
        public String getAddress(Context context){
            return this._address;
        }

        public boolean isShortCode() {
            Pattern pattern = Pattern.compile("[a-zA-Z]");
            Matcher matcher = pattern.matcher(this.address);
            return PhoneNumberUtils.isWellFormedSmsAddress(this.address) && !matcher.find();
        }

        private String formatPhoneNumbers(Context context, String data) throws NumberParseException {
            String formattedString = data.replaceAll("%2B", "+")
                    .replaceAll("%20", "")
                    .replaceAll("-", "")
                    .replaceAll("\\s", "");

            if(!PhoneNumberUtils.isWellFormedSmsAddress(formattedString))
                return data;

            // Remove any non-digit characters except the plus sign at the beginning of the string
            String strippedNumber = formattedString.replaceAll("[^0-9+]", "");

            if(strippedNumber.length() > 6) {
                // If the stripped number starts with a plus sign followed by one or more digits, return it as is
                if (!strippedNumber.matches("^\\+\\d+")) {
                    String dialingCode = getUserCountry(context);
                    strippedNumber = "+" + dialingCode + data;
                }
                return strippedNumber;
            }

            // If the stripped number is not a valid phone number, return an empty string
            return data;
        }

        public String getContactName(Context context) {
            try {
                return Contacts.retrieveContactName(context, getAddress(context));
            } catch(Exception e) {
                e.printStackTrace();
            }
            return this.address;
        }

        /**
         *
         * @param context
         * @return ENCRYPTION_STATE: Informs about the encryption with current address that holds
         * this entity. Remember, it is always the address' state with you - not yours!
         * @throws GeneralSecurityException
         * @throws IOException
         */
        public ENCRYPTION_STATE getEncryptionState(Context context) throws GeneralSecurityException, IOException {
            SecurityECDH securityECDH = new SecurityECDH(context);
            if(securityECDH.peerAgreementPublicKeysAvailable(context, this.getAddress(context)) &&
                    securityECDH.hasPrivateKey(getAddress(context))) {
                return ENCRYPTION_STATE.RECEIVED_PENDING_AGREEMENT;
            }
            else if (securityECDH.peerAgreementPublicKeysAvailable(context, this.getAddress(context))) {
                return ENCRYPTION_STATE.RECEIVED_AGREEMENT_REQUEST;
            }
            else if(securityECDH.hasPrivateKey(getAddress(context))) {
                return ENCRYPTION_STATE.SENT_PENDING_AGREEMENT;
            }
            else if (securityECDH.hasSecretKey(address)) {
                return ENCRYPTION_STATE.ENCRYPTED;
            }
            return ENCRYPTION_STATE.NOT_ENCRYPTED;
        }

        /**
         *
         * @param context
         * @return byte[] : Returns the public key. Remember this is your
         * primary key (you being whomever is initiating the handshake).
         * @throws GeneralSecurityException
         * @throws IOException
         */
        public byte[] generateAgreements(Context context) throws GeneralSecurityException, IOException {
            SecurityECDH securityECDH = new SecurityECDH(context);
            PublicKey publicKey = securityECDH.generateKeyPair(context, getAddress(context));

            // TODO: refactor txAgreementFormatter -> why is exist?
            return SecurityHelpers.txAgreementFormatter(publicKey.getEncoded());
        }

        /**
         *
         * @param context
         * @return byte[] : Returns the public key generated from the peer agreement key.
         * Remember this is your primary key (you being whomever is initiating the handshake).
         * @throws GeneralSecurityException
         * @throws IOException
         */
        public byte[] agreePeerRequest(Context context) throws GeneralSecurityException, IOException {
            SecurityECDH securityECDH = new SecurityECDH(context);
            byte[] peerPublicKey = Base64.decode(securityECDH.getPeerAgreementPublicKey(address),
                    Base64.DEFAULT);

            KeyPair keyPair = securityECDH.generateKeyPairFromPublicKey(peerPublicKey);
            byte[] secret = securityECDH.generateSecretKey(peerPublicKey, address);
            securityECDH.securelyStoreSecretKey(address, secret);

            return keyPair.getPublic().getEncoded();
        }

        public byte[] getSecretKey(Context context) throws GeneralSecurityException, IOException {
            SecurityECDH securityECDH = new SecurityECDH(context);
            return Base64.decode(securityECDH.securelyFetchSecretKey(getAddress(context)), Base64.DEFAULT);
        }

        public String encryptContent(Context context, String data) throws Throwable {
            byte[] encryptedContent = SecurityECDH.encryptAES(data.getBytes(StandardCharsets.UTF_8),
                    getSecretKey(context));
            return Base64.encodeToString(encryptedContent, Base64.DEFAULT);
        }

        public void call(Context context) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + getAddress(context)));

            context.startActivity(callIntent);
        }
    }
}
