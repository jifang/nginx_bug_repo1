package co.visbit.myapplication;

/**
 * Created by jifang on 3/5/18.
 * Copyright © 2016 visbit. All rights reserved.
 */

public interface TestUtil {

  void assertTrue(boolean flag);

  void assertEquals(long expected, long actual);

  int logVerbose(String tag, String msg);

  int logDebug(String tag, String msg);

  int logInfo(String tag, String msg);

  int logError(String tag, String msg);
}
