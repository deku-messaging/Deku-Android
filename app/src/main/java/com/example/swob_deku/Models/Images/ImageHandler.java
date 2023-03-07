package com.example.swob_deku.Models.Images;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.util.Base64;
import android.util.Log;

import com.example.swob_deku.Commons.DataHelper;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.Models.Security.SecurityAES;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ImageHandler {

    Uri imageUri;
    Context context;
    public Bitmap bitmap;

    String iv = "1234567890123456";
    String secretKey = "12345678901234561234567890123456";

    static final String IMAGE_HEADER = "--DEKU_IMAGE_HEADER--";
    static final int MAX_NUMBER_SMS = 39;

    public ImageHandler(Context context, Uri imageUri) throws IOException {
        this.imageUri = imageUri;
        this.context = context;
        this.bitmap = MediaStore.Images.Media.getBitmap(this.context.getContentResolver(), this.imageUri);
    }

    public byte[] getBitmapByte(Bitmap bitmap) {
        int size = bitmap.getRowBytes() * bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(byteBuffer);
        return byteBuffer.array();
    }

    public double shannonEntropy(byte[] values) {
        Map<Byte, Integer> map = new HashMap<Byte, Integer>();
        // count the occurrences of each value
        for (Byte sequence : values) {
            if (!map.containsKey(sequence)) {
                map.put(sequence, 0);
            }
            map.put(sequence, map.get(sequence) + 1);
        }

        // calculate the entropy
        double result = 0.0;
        for (Byte sequence : map.keySet()) {
            double frequency = (double) map.get(sequence) / values.length;
            result -= frequency * (Math.log(frequency) / Math.log(2));
        }

        return result;
    }


    public int[] getDimension(int width, int height, int ratio) {
        double wr = (double) width / height;
        double hr = (double) height / width;

        if(wr > hr) {
            width = ratio;
            height = (int) Math.round(ratio * hr);
        }
        else if(hr > wr) {
            height = ratio;
            width = (int) Math.round(ratio * wr);
        }
        else {
            width = ratio;
            height = ratio;
        }

        return new int[]{width, height};
    }

    public Bitmap resizeImage(int resValue) throws IOException {
        // use ratios for compressions rather than just raw values
        if(this.bitmap.getWidth() < resValue && this.bitmap.getHeight() < resValue)
            return this.bitmap;

        int[] dimensions = getDimension(this.bitmap.getWidth(), this.bitmap.getHeight(), resValue);
        int width = dimensions[0];
        int height = dimensions[1];


        return Bitmap.createScaledBitmap(this.bitmap, width, height, true);
    }

    public byte[] encryptImage(byte[] imageBytes) throws Throwable {
        SecurityAES aes = new SecurityAES();

        byte[] bytesJpegEncryption = aes.encrypt(iv.getBytes(StandardCharsets.UTF_8),
                imageBytes, secretKey.getBytes(StandardCharsets.UTF_8));

        return bytesJpegEncryption;
    }

    public byte[] compressImage(int compressionRatio, Bitmap.CompressFormat compressFormat) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        this.bitmap.compress(compressFormat, compressionRatio, byteArrayOutputStream);

        byte[] compressedBitmapBytes = byteArrayOutputStream.toByteArray();
        return compressedBitmapBytes;
    }

    public byte[] compressImage(int compressionRatio) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        Bitmap.CompressFormat bitmapCompressionFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ?
                Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

        this.bitmap.compress(bitmapCompressionFormat, compressionRatio, byteArrayOutputStream);

        byte[] compressedBitmapBytes = byteArrayOutputStream.toByteArray();
        return compressedBitmapBytes;
    }

    public byte[] compressImage(int compressionRatio, Bitmap imageBitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        Bitmap.CompressFormat bitmapCompressionFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ?
                Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

        imageBitmap.compress(bitmapCompressionFormat, compressionRatio, byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    public static boolean isImageBody(byte[] data) {
        /**
         * 0 = Reference ID
         * 1 = Message ID
         * 2 = Total number of messages
         */
        return data.length > 2
                && Byte.toUnsignedInt(data[0]) >= SMSHandler.ASCII_MAGIC_NUMBER
                && Byte.toUnsignedInt(data[1]) >= 0;
    }

    private boolean isImageHeader(SMS sms) {
        byte[] data = Base64.decode(sms.getBody(), Base64.DEFAULT);

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

        return bitmap != null || (data.length > 3
                && Byte.toUnsignedInt(data[0]) >= SMSHandler.ASCII_MAGIC_NUMBER
                && Byte.toUnsignedInt(data[1]) >= 0 && Byte.toUnsignedInt(data[2]) <= MAX_NUMBER_SMS);
    }


    public static byte[] extractMeta(byte[] data) {
        return new byte[]{data[0], data[1]};
    }

    public static String[] fetchImage(Context context, byte[] data, long messageId) {
        // TODO: build for len so not to waste compute

        int len = Byte.toUnsignedInt(data[2]);
        int imageCounter = 0;

        String[] messageIds = new String[len];
        Cursor imageThreadIdCursor = SMSHandler.fetchSMSMessageThreadIdFromMessageId(context, messageId);
        if(imageThreadIdCursor.moveToFirst()) {
            int indexOfThreadId = imageThreadIdCursor.getColumnIndex(Telephony.TextBasedSmsColumns.THREAD_ID);
            String threadId = imageThreadIdCursor.getString(indexOfThreadId);

            String RIL = Base64.encodeToString(new byte[]{data[0]}, Base64.NO_PADDING)
                    .replaceAll("\\n", "");

            Cursor cursorImageCursor = SMSHandler.fetchSMSForImagesByThreadId(context, RIL, threadId);

            if (cursorImageCursor.moveToFirst()) {
                do {
                    SMS imageSMS = new SMS(cursorImageCursor);
                    byte[] imgBody = Base64.decode(imageSMS.getBody(), Base64.NO_PADDING);
                    int id = Byte.toUnsignedInt(imgBody[1]);
                    messageIds[id] = imageSMS.id;

                    ++imageCounter;
                } while (cursorImageCursor.moveToNext());
            }
            cursorImageCursor.close();
        }
        imageThreadIdCursor.close();
        return imageCounter == len ? messageIds : null;
    }

    public static Bitmap buildImage(byte[][] unstructuredImageBytes ) throws IOException {
        byte[] structuredImageBytes = SMSHandler.rebuildStructuredSMSMessage(unstructuredImageBytes);
        return BitmapFactory.decodeByteArray(structuredImageBytes, 0, structuredImageBytes.length);
    }

    public static void rebuildImage(Context context, String[] messageIds) {
        StringBuilder stringBuffer = new StringBuilder();

        for(int i=0; i < messageIds.length; ++i) {
            String messageId = messageIds[i];
            Cursor cursor = SMSHandler.fetchSMSInboxById(context, messageId);
            if(cursor.moveToFirst()) {
                SMS sms = new SMS(cursor);

                byte[] data = Base64.decode(sms.getBody(), Base64.NO_PADDING);

                int offset = i == 0 ? 3 : 2;

                String body = IMAGE_HEADER + Base64.encodeToString(data, offset, data.length, Base64.NO_PADDING);
                stringBuffer.append(body);

                // TODO: Delete message
                if( i != 0)
                    SMSHandler.deleteMessage(context, sms.getId());
            }
            cursor.close();
        }
        SMSHandler.updateMessage(context, messageIds[0], stringBuffer.toString());
    }

    public static boolean canComposeImage(Context context, String RIL) {
        Cursor cursorImageCursor = SMSHandler.fetchSMSForImagesByRIL(context, RIL);
        Log.d(ImageHandler.class.getName(), "Data image header RIL: " + RIL);
        Log.d(ImageHandler.class.getName(), "Data image header counter: " + cursorImageCursor.getCount());
        if(cursorImageCursor.moveToFirst()) {
            SMS sms = new SMS(cursorImageCursor);
            byte[] data = Base64.decode(sms.getBody(), Base64.NO_PADDING);
            Log.d(ImageHandler.class.getName(), "Data image ref: " + Byte.toUnsignedInt(data[0]));
            // TODO: check if data is image
            int len = Byte.toUnsignedInt(data[2]);

            StringBuilder query = new StringBuilder();
            String[] parameters = new String[len];

            for(Integer i=0; i<len; ++i ) {
                if(i + 1 == len)
                    query.append(Telephony.TextBasedSmsColumns.BODY + " like ?");
                else
                    query.append(Telephony.TextBasedSmsColumns.BODY + " like ? OR ");

                parameters[i] = Base64.encodeToString(new byte[]{data[0], i.byteValue()}, Base64.NO_PADDING)
                        .replaceAll("\\n", "") + "%";
            }

            Cursor cursor = SMSHandler.fetchSMSForImages(context, query.toString(), parameters, sms.getThreadId());
            Log.d(ImageHandler.class.getName(), "Data image founder counter: " + cursor.getCount() + "/" + len);
            if(cursor.getCount() >= len) {
                cursor.close();
                return true;
            }
        }
        cursorImageCursor.close();
        return false;
    }
}
