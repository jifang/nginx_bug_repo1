package co.visbit.myapplication;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by jifang on 4/5/17.
 * Copyright © 2016 visbit. All rights reserved.
 */

@RunWith(AndroidJUnit4.class)
public class OkHttpTest extends VbTest {

  @Before
  public void setUp() {
    super.setUp();
  }

  @Test
  public void testMultipleDownloads() throws Exception {
    super.testMultipleDownloads();
  }

  @Override
  protected String getHost() {
    return "10.0.0.186";
  }

  @Override
  public void assertTrue(boolean flag) {
    Assert.assertTrue(flag);
  }

  @Override
  public void assertEquals(long expected, long actual) {
    Assert.assertEquals(expected, actual);
  }

  @Override
  public int logVerbose(String tag, String msg) {
    return Log.v(tag, msg);
  }

  @Override
  public int logDebug(String tag, String msg) {
    return Log.d(tag, msg);
  }

  @Override
  public int logInfo(String tag, String msg) {
    return Log.i(tag, msg);
  }

  @Override
  public int logError(String tag, String msg) {
    return Log.e(tag, msg);
  }

}
