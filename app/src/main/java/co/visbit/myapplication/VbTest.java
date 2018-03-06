package co.visbit.myapplication;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by jifang on 3/5/18.
 * Copyright © 2016 visbit. All rights reserved.
 */

public abstract class VbTest implements TestUtil {

  public static final String TAG = "VB_Test";

  private static final int BUFFER_SIZE = 8192;

  // Actual number of files hosted on server
  private static final int SERVER_FILE_COUNT = 100;

  // Each thread will download TEST_COUNT of files
  private static final int TEST_COUNT = 100;
  private static final int CONCURRENCY = 1;
  final int CANCEL = 5; // The less, the more chance to get cancelled

  OkHttpClient client;

  private AtomicInteger loadCount = new AtomicInteger(0);
  private long lastLoadMarkTime;
  private long testStartTime;
  private AtomicLong totalLatency = new AtomicLong();
  private AtomicLong totalLoadTime = new AtomicLong();

  private volatile boolean success = true;

  public void setUp() {
    client = getHttpStreamingClient();
    lastLoadMarkTime = System.currentTimeMillis();
  }

  public void testMultipleDownloads() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    final CountDownLatch signal = new CountDownLatch(CONCURRENCY);
    testStartTime = System.currentTimeMillis();

    for (int i = 0; i < CONCURRENCY; ++i) {
      final int threadNo = i + 1;
      executor.submit(new Runnable() {
        @Override
        public void run() {
          OkHttpDataSource dataSource = new OkHttpDataSource(client, "");
          for (int j = 0; j < TEST_COUNT; ++j) {
            String url = null;
            int index = (j + 1) * threadNo;
            try {
              url = getUrl(index).toString();
              if (threadNo > 1 && j == 0) {
                Thread.sleep(500);
              }
              logVerbose(TAG, "[" + threadNo + "] downloading:" + index);
              download(dataSource, url);
            } catch (Exception e) {
              Throwable t = e;
              while (t != null) {
                logError(TAG, t.getMessage() + " " + url);
                t = e.getCause();
              }
              e.printStackTrace();
              success = false;
            }
            if (!success) {
              logInfo(TAG, "[" + index + "] exit");
              break;
            }
          }
          signal.countDown();
        }
      });
    }

    signal.await();

    String message = "Test finished: " + (System.currentTimeMillis() - testStartTime)/1000 + "s"
        + " load: " + loadCount.get()
        + " cancel: " + cancelCount.get()
//        + " time: " + totalLoadTime.get() / (loadCount.get() - cancelCount.get()) + "ms"
        + " latency: " + totalLatency.get() / loadCount.get() + "ms";
    logInfo(TAG, message);
    assertTrue(success);
  }

  private AtomicInteger cancelCount = new AtomicInteger(0);

  private void download(OkHttpDataSource dataSource, String url) throws IOException {
    if (loadCount.incrementAndGet() % 100 == 0) {
      long now = System.currentTimeMillis();
      long duration = now - lastLoadMarkTime;
      lastLoadMarkTime = now;
      logInfo(TAG, "Load #100 " + duration + " ms");
    }
    logVerbose(TAG, "Download:" + url);
    byte[] buffer = new byte[BUFFER_SIZE];

    long start = System.currentTimeMillis();
    long bytesToRead = dataSource.open(url);
    long latency = System.currentTimeMillis() - start;
    long averageLatency = totalLatency.addAndGet(latency) / loadCount.get();

    long totalRead = 0;
    boolean cancelled = false;
    while (true) {
      long read = dataSource.read(buffer, 0, BUFFER_SIZE);
      if (read == -1) {
        break;
      }
      int value = new Random().nextInt();
      cancelled = (value % CANCEL) == 0;
      if (cancelled) {
        logDebug(TAG, "Cancel request:" + url + " read:" + totalRead);
        logVerbose(TAG, "Cancel count:" + cancelCount.incrementAndGet() + " total run:" +
            (System.currentTimeMillis() - testStartTime) / 1000 + " s (" + value);
        dataSource.cancel();
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        break;
      }
      totalRead += read;
    }

    long duration = System.currentTimeMillis() - start - latency;
    duration = Math.max(duration, 1);
    long speed = totalRead / duration;

    if (!cancelled) {
      totalLoadTime.addAndGet(duration + latency);
    }

    logVerbose(TAG, "Latency: " + averageLatency + "ms speed: " + speed + " kB/s");
    if (!cancelled) {
      assertEquals(bytesToRead, totalRead);
    }
    dataSource.close();

  }

  private synchronized OkHttpClient getHttpStreamingClient() {
    if (client == null) {
      client = getUnsafeOkHttpClient();
    }
    return client;
  }

  protected String getHost() {
    return "localhost";
  }

  private HttpUrl getUrl(int index) {
    int mod = index % SERVER_FILE_COUNT;
    if (mod == 0) {
      mod = 100;
    }
    return new HttpUrl.Builder()
        .scheme("https")
        .host(getHost())
        .addPathSegment("files")
        .addPathSegment(String.valueOf(mod))
        .build();
  }

  public static final String NETWORK = "VB_Network";

  private class LoggingInterceptor implements Interceptor {
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
      Request request = chain.request();

      long t1 = System.nanoTime();
      logVerbose(NETWORK, String.format("Sending request %s on %s%n%s",
          request.url(), chain.connection(), request.headers()));
      Response response = chain.proceed(request);

      long t2 = System.nanoTime();
      logVerbose(NETWORK, String.format(Locale.US, "Received response for %s (%s) in %.1fms%n%s",
          response.request().url(),
          response.protocol().toString(),
          (t2 - t1) / 1e6d, response.headers()));
      return response;
    }
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts = new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                throws CertificateException {}

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                throws CertificateException {}

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return new java.security.cert.X509Certificate[]{};
            }
          }
      };

      // Install the all-trusting trust manager
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      // Create an ssl socket factory with our all-trusting manager
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      OkHttpClient.Builder builder = new OkHttpClient.Builder();
      builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
      builder.hostnameVerifier(new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      });

      ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
          .cipherSuites(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA)
          .build();

      List<Protocol> protocols = new ArrayList<>();
      protocols.add(Protocol.HTTP_1_1);
      protocols.add(Protocol.HTTP_2);


      final Logger logger = Logger.getLogger(okhttp3.internal.http2.Http2.class.getName());
      logger.setLevel(Level.FINE);

      OkHttpClient okHttpClient = builder
          .connectionSpecs(Collections.singletonList(spec))
          .protocols(protocols)
//          .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
          .readTimeout(120, TimeUnit.SECONDS)
          .addNetworkInterceptor(new LoggingInterceptor())
          .build();

      return okHttpClient;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
