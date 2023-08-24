/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.hadoop.classification.InterfaceStability.Unstable;

/**
 * Copied from guava source code v15 (LimitedInputStream)
 * Guava deprecated LimitInputStream in v14 and removed it in v15. Copying this class here
 * allows to be compatible with guava 11 to 15+.
 *
 * Originally: org.apache.hadoop.hbase.io.LimitInputStream
 */
@Unstable
public final class LimitInputStream extends FilterInputStream {

    private long left;

    private long mark = -1;

    public LimitInputStream(InputStream in, long limit) {
        super(in);
        checkNotNull(in);
        checkArgument(limit >= 0, "limit must be non-negative");
        left = limit;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), left);
    }

    // it's okay to mark even if mark isn't supported, as reset won't work
    @Override
    public synchronized void mark(int readLimit) {
        in.mark(readLimit);
        mark = left;
    }

    @Override
    public int read() throws IOException {
        if (left == 0) {
            return -1;
        }
        int result = in.read();
        if (result != -1) {
            --left;
        }
        return result;
    }

    public int internal$read2(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (left == 0) {
            return -1;
        }
        EXIT81 = true;
        len = (int) Math.min(len, left);
        int result = in.read(b, off, len);
        if (result != -1) {
            left -= result;
        }
        return result;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (!in.markSupported()) {
            throw new IOException("Mark not supported");
        }
        if (mark == -1) {
            throw new IOException("Mark not set");
        }
        in.reset();
        left = mark;
    }

    @Override
    public long skip(long n) throws IOException {
        n = Math.min(n, left);
        long skipped = in.skip(n);
        left -= skipped;
        return skipped;
    }

    private boolean EXIT89 = false;

    private boolean EXIT81 = false;

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            if (!(this.left == 0 || this.left == 4 || this.left == 7)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(3);
            }
        } catch (Exception e) {
        }
        int returnValue;
        returnValue = internal$read2(b, off, len);
        try {
            if (EXIT89 && !(this.left == 0)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(4);
            }
        } catch (Exception e) {
        }
        try {
            if (EXIT81 && !(this.left == 0)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(5);
            }
        } catch (Exception e) {
        }
        EXIT89 = false;
        EXIT81 = false;
        return returnValue;
    }
}
