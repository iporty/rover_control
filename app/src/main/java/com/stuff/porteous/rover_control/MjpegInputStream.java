package com.stuff.porteous.rover_control;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpResponse;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Properties;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MjpegInputStream extends DataInputStream {
    private final byte[] SOI_MARKER = { (byte) 0xFF, (byte) 0xD8 };
    private final byte[] EOF_MARKER = { (byte) 0xFF, (byte) 0xD9 };
    private final String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;
    private byte[] frameData_;
    private int frameDataSize_ = 0;
    private byte[] header_;
    private int headerLen_ = 0;
    private BitmapFactory.Options bm_options_ = null;
    private byte[] temp_storage_ = null;
    private final static int DECODE_TEMP_STORAGE_SIZE = 10000000;
    private Bitmap bitmap_ = null;
    private int avg_count_ = 0;
    private double avg_ = 0;

    public static MjpegInputStream read(String url_address) {
        InputStream res;
        URL url = null;
        try {
            url = new URL(url_address);

        } catch(Exception e) {
            Log.e("MjpegInputStream", e.toString());
        }
        try {
            HttpURLConnection httpclient = (HttpURLConnection) url.openConnection();

            //res = httpclient.getInputStream();
            return new MjpegInputStream(httpclient.getInputStream());
        } catch (ClientProtocolException e) {
            Log.e("Client Protocol Exception", e.toString());
        } catch (IOException e) {
            Log.e("IOException", e.toString());
        }
        return null;


    }

    public MjpegInputStream(InputStream in) {

        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
        header_ = new byte[HEADER_MAX_LENGTH];
        bm_options_ = new BitmapFactory.Options();
        temp_storage_ = new byte[DECODE_TEMP_STORAGE_SIZE];
        bm_options_.inTempStorage = temp_storage_;
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence, int max_length, byte[] header) throws IOException {
        int seqIndex = 0;
        byte c;
        try {
            for (int i = 0; i < max_length; i++) {
                c = (byte) in.readUnsignedByte();
                header[i] = c;
                if (c == sequence[seqIndex]) {
                    seqIndex++;
                    if (seqIndex == sequence.length) return i + 1;
                } else seqIndex = 0;
            }
            return -1;
        } catch (Exception e) {
            Log.e("getEndOfSequence", e.toString());
            return -1;
        }
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = getEndOfSeqeunce(in, sequence, HEADER_MAX_LENGTH, header_);
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    public Bitmap readMjpegFrame() throws IOException {
        long start_time = System.nanoTime();
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        readFully(header_, 0, headerLen);
        try {
            mContentLength = parseContentLength(header_);
        } catch (NumberFormatException nfe) {
            mContentLength = getEndOfSeqeunce(this, EOF_MARKER, FRAME_MAX_LENGTH, frameData_);
        }
        reset();
        if (frameDataSize_ < mContentLength) {
            frameData_ = new byte[mContentLength];
            frameDataSize_ = mContentLength;
        }
        //frameData_ = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData_, 0, mContentLength);
        //return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData_));
        bm_options_.inBitmap = bitmap_;
        bitmap_ = BitmapFactory.decodeByteArray(frameData_, 0, mContentLength);

        long end_time = System.nanoTime();
        long duration = end_time - start_time;
        if (avg_count_ == 10) {
            Log.d("readMjpegTime", "****************" + String.valueOf(avg_ / 1E9));
            avg_count_ = 0;
            avg_ = 0;
        } else {
            avg_ = avg_ * avg_count_;
            avg_ = avg_ + duration;
            avg_count_ = avg_count_ + 1;
            avg_ = avg_ / avg_count_;
        }
        return bitmap_;

        //return BitmapFactory.decodeByteArray(frameData_, 0, mContentLength);
    }
}
