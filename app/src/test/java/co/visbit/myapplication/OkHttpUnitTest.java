package co.visbit.myapplication;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class OkHttpUnitTest extends VbTest {

  @Before
  public void setUp() {
    super.setUp();
  }

  @Test
  public void testMultipleDownloads() throws Exception {
    super.testMultipleDownloads();
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
    System.out.println(tag + " " + msg);
    return 0;
  }

  @Override
  public int logDebug(String tag, String msg) {
    return logVerbose(tag, msg);
  }

  @Override
  public int logInfo(String tag, String msg) {
    return logVerbose(tag, msg);
  }

  @Override
  public int logError(String tag, String msg) {
    return logVerbose(tag, msg);
  }

}