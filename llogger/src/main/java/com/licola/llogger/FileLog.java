package com.licola.llogger;

import static com.licola.llogger.LLogger.mapperType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by LiCola on 2018/5/14.
 */
class FileLog {

  private static final char SPACING = ' ';
  private static final String FILE_FORMAT = ".log";
  static final String DEFAULT_FILE_PREFIX = "LLogger_";

  private static final ThreadLocal<SimpleDateFormat> FORMAT_FILE = new ThreadLocal<SimpleDateFormat>() {
    @Override
    protected SimpleDateFormat initialValue() {
      return new SimpleDateFormat("yyyy-MM-dd_HH", Locale.CHINA);
    }
  };

  private static final ThreadLocal<SimpleDateFormat> FORMAT_INFO = new ThreadLocal<SimpleDateFormat>() {
    @Override
    protected SimpleDateFormat initialValue() {
      return new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);
    }
  };

  private final String logFilePrefix;
  private final File logFileDir;

  FileLog(File logFileDir, String logFilePrefix) {
    Utils.checkDirFile(logFileDir);
    Utils.checkNotEmpty(logFilePrefix);
    this.logFileDir = logFileDir;
    this.logFilePrefix = logFilePrefix;
  }

  public String printFileLog(long timeMillis, int type,
      String tag, String msg) throws IOException {

    Utils.checkAndCreateDir(logFileDir);

    String timeFileInfo = FORMAT_FILE.get().format(timeMillis);
    String logFileName = logFilePrefix + timeFileInfo + FILE_FORMAT;

    File logFile = new File(logFileDir, logFileName);
    boolean createFileFlag = createLogFile(logFile);

    String timeInfo = FORMAT_INFO.get().format(timeMillis);
    try {
      writeInfo(logFile, timeInfo, type, tag, msg);
    } catch (IOException e) {
      throw new IOException("log info write fail", e);
    }

    if (createFileFlag) {
      return logFile.getAbsolutePath();
    } else {
      return null;
    }
  }

  public File makeZipFile(String zipFileName, long beginTime)
      throws IOException {

    List<File> files = fetchLogFiles(beginTime);

    File zipFile = new File(logFileDir, zipFileName);

    if (zipFile.exists()) {
      //防止已经存在的文件影响
      zipFile.delete();
    }

    FileOutputStream zipFileOutputStream = new FileOutputStream(zipFile);

    ZipOutputStream zipOutputStream = new ZipOutputStream(zipFileOutputStream);

    try {
      for (File file : files) {
        writeZipFile(zipOutputStream, file);
      }
    } catch (IOException e) {
      throw new IOException("压缩日志文件异常", e);
    }

    try {
      zipOutputStream.close();
    } catch (IOException e) {
    }

    try {
      zipFileOutputStream.close();
    } catch (IOException e) {
    }

    return zipFile;
  }

  private static void writeZipFile(ZipOutputStream zipOutputStream, File file) throws IOException {
    FileInputStream fileInputStream = null;
    try {
      fileInputStream = new FileInputStream(file);
      ZipEntry zipEntry = new ZipEntry(file.getName());
      zipOutputStream.putNextEntry(zipEntry);

      byte[] bytes = new byte[1024];
      int length;
      while ((length = fileInputStream.read(bytes)) >= 0) {
        zipOutputStream.write(bytes, 0, length);
      }
    } finally {
      if (fileInputStream != null) {
        fileInputStream.close();
      }
    }

  }

  public List<File> fetchLogFiles(long beginTime)
      throws FileNotFoundException {
    if (logFileDir == null || !logFileDir.exists()) {
      throw new FileNotFoundException("logFileDir == null or not exists");
    }

    File[] files = logFileDir.listFiles();

    if (files == null || files.length == 0) {
      throw new FileNotFoundException(logFileDir.getAbsolutePath() + " is empty dir");
    }

    ArrayList<File> logFiles = new ArrayList<>();
    for (File file : files) {
      String fileName = file.getName();
      if (!fileName.startsWith(logFilePrefix) || !fileName.endsWith(FILE_FORMAT)) {
        //去除非目标日志 即非固定前缀和固定后缀的文件名
        continue;
      }

      if (beginTime == 0) {
        //开始时间为0 表示没有限制条件
        logFiles.add(file);
      } else {
        String timeFileInfo = fileName
            .substring(logFilePrefix.length(), fileName.length() - FILE_FORMAT.length());
        long fileTime = getFileTime(timeFileInfo);
        if (fileTime >= beginTime) {
          //log文件保存时间 >= 限定开始时间 即在限定时间之后的日志
          logFiles.add(file);
        }
      }
    }

    if (logFiles.isEmpty()) {
      throw new FileNotFoundException("No files meet the conditions of time");
    }

    return logFiles;
  }

  private static long getFileTime(String timeFileInfo) {

    Date date;
    try {
      date = FORMAT_FILE.get().parse(timeFileInfo);
    } catch (ParseException e) {
      date = null;
    }

    return date != null ? date.getTime() : 0;
  }

  private static void writeInfo(File logFile, String timePrefix, int type, String tag,
      String msg)
      throws IOException {

    String threadName = Thread.currentThread().getName();

    String output = timePrefix
        + SPACING
        + threadName
        + SPACING
        + mapperType(type)
        + '/'
        + tag
        + ':'
        + msg
        + "\n";

    mappedByteBufferWrite(logFile, output);
  }

  private static void mappedByteBufferWrite(File logFile, String output)
      throws IOException {
    byte[] srcByte = output.getBytes();
    int length = srcByte.length;
    RandomAccessFile randomAccessFile = null;

    try {
      randomAccessFile = new RandomAccessFile(logFile, "rw");
      MappedByteBuffer byteBuffer = randomAccessFile
          .getChannel()
          .map(MapMode.READ_WRITE, logFile.length(), length);
      for (byte aSrcByte : srcByte) {
        byteBuffer.put(aSrcByte);
      }
    } finally {
      if (randomAccessFile != null) {
        randomAccessFile.close();
      }
    }
  }

  private static boolean createLogFile(File logFile) throws IOException {

    if (logFile.exists()) {
      if (!logFile.isFile()) {
        throw new IOException(
            "file " + logFile.getAbsolutePath() + " is not file cannot input log");
      }
      return false;
    } else {
      try {
        return logFile.createNewFile();
      } catch (IOException e) {
        throw new IOException("file" + logFile.getAbsolutePath() + " createNewFile fail", e);
      }
    }
  }

}
