/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.visbit.myapplication;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class OkHttpDataSource {

  private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

  private OkHttpClient okHttpClient;
  private final String userAgent;

  private Response response;
  private Call call; /* + VBCHANGE */
  private InputStream responseByteStream;
  private boolean opened;

  private long bytesToRead;

  private long bytesRead;

  public OkHttpDataSource(OkHttpClient client, String userAgent) {
    this.okHttpClient = client;
    this.userAgent = userAgent;
  }


  public long open(String dataSpec)  {
    this.bytesRead = 0;
    Request request = makeRequest(dataSpec);
    try {
      call = okHttpClient.newCall(request); /* + VBCHANGE */
      response = call.execute();
      responseByteStream = response.body().byteStream();
    } catch (IOException e) {
      throw new RuntimeException("Unable to connect to " + dataSpec.toString());
    }

    int responseCode = response.code();

    // Check for a valid response code.
    if (!response.isSuccessful()) {
      closeConnectionQuietly();
      throw new RuntimeException(String.valueOf(responseCode));
    }

    // Determine the length of the data to be read, after skipping.
    bytesToRead = response.body().contentLength();

    opened = true;

    return bytesToRead;
  }

  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return readInternal(buffer, offset, readLength);
  }

  public void close() {
    if (opened) {
      opened = false;
      closeConnectionQuietly();
    }
  }

  public void cancel() {
    call.cancel();
  } /* + VBCHANGE */


  /**
   * Establishes a connection.
   */
  private Request makeRequest(String dataSpec) {
    boolean allowGzip = true;

    HttpUrl url = HttpUrl.parse(dataSpec);
    Request.Builder builder = new Request.Builder().url(url);

    builder.addHeader("User-Agent", userAgent);
    if (!allowGzip) {
      builder.addHeader("Accept-Encoding", "identity");
    }

    return builder.build();
  }

  public static final int LENGTH_UNBOUNDED = -1;
  public static final int RESULT_END_OF_INPUT = -1;

  private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
    readLength = bytesToRead == LENGTH_UNBOUNDED ? readLength
        : (int) Math.min(readLength, bytesToRead - bytesRead);
    if (readLength == 0) {
      // We've read all of the requested data.
      return RESULT_END_OF_INPUT;
    }

    int read = responseByteStream.read(buffer, offset, readLength);
    if (read == -1) {
      if (bytesToRead != LENGTH_UNBOUNDED && bytesToRead != bytesRead) {
        // The server closed the connection having not sent sufficient data.
        throw new EOFException();
      }
      return RESULT_END_OF_INPUT;
    }

    bytesRead += read;
    return read;
  }

  /**
   * Closes the current connection quietly, if there is one.
   */
  private void closeConnectionQuietly() {
    response.body().close();
    response = null;
    responseByteStream = null;
  }

}
