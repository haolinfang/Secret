package com.ionicframework.cordova.webview.online;

import java.io.IOException;
import java.io.InputStream;

public abstract class InputStreamWrapper extends InputStream {
  protected final InputStream wrapped;

  public InputStreamWrapper(InputStream wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public int read() throws IOException {
    return wrapped.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return wrapped.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return wrapped.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    return wrapped.skip(n);
  }

  @Override
  public int available() throws IOException {
    return wrapped.available();
  }

  @Override
  public synchronized void mark(int readlimit) {
    wrapped.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    wrapped.reset();
  }

  @Override
  public boolean markSupported() {
    return wrapped.markSupported();
  }

  @Override
  public abstract void close() throws IOException;
}
