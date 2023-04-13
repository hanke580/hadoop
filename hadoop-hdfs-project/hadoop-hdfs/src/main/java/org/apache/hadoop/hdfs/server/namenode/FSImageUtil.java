/**
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
package org.apache.hadoop.hdfs.server.namenode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.LayoutVersion.Feature;
import org.apache.hadoop.hdfs.server.namenode.FSImageFormatProtobuf.Loader;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.FileSummary;
import org.apache.hadoop.io.compress.CompressionCodec;

@InterfaceAudience.Private
public final class FSImageUtil {

    public static final byte[] MAGIC_HEADER = "HDFSIMG1".getBytes(StandardCharsets.UTF_8);

    public static final int FILE_VERSION = 1;

    public static boolean checkFileFormat(RandomAccessFile file) throws IOException {
        if (file.length() < Loader.MINIMUM_FILE_LENGTH)
            return false;
        byte[] magic = new byte[MAGIC_HEADER.length];
        file.readFully(magic);
        if (!Arrays.equals(MAGIC_HEADER, magic))
            return false;
        return true;
    }

    public static FileSummary internal$loadSummary(RandomAccessFile file) throws IOException {
        final int FILE_LENGTH_FIELD_SIZE = 4;
        long fileLength = file.length();
        file.seek(fileLength - FILE_LENGTH_FIELD_SIZE);
        int summaryLength = file.readInt();
        if (summaryLength <= 0) {
            throw new IOException("Negative length of the file");
        }
        file.seek(fileLength - FILE_LENGTH_FIELD_SIZE - summaryLength);
        byte[] summaryBytes = new byte[summaryLength];
        file.readFully(summaryBytes);
        FileSummary summary = FileSummary.parseDelimitedFrom(new ByteArrayInputStream(summaryBytes));
        if (summary.getOndiskVersion() != FILE_VERSION) {
            throw new IOException("Unsupported file version " + summary.getOndiskVersion());
        }
        if (!NameNodeLayoutVersion.supports(Feature.PROTOBUF_FORMAT, summary.getLayoutVersion())) {
            throw new IOException("Unsupported layout version " + summary.getLayoutVersion());
        }
        return summary;
    }

    public static InputStream internal$wrapInputStreamForCompression(Configuration conf, String codec, InputStream in) throws IOException {
        if (codec.isEmpty())
            return in;
        FSImageCompression compression = FSImageCompression.createCompression(conf, codec);
        CompressionCodec imageCodec = compression.getImageCodec();
        return imageCodec.createInputStream(in);
    }

    public static FileSummary loadSummary(RandomAccessFile file) throws IOException {
        FileSummary returnValue;
        returnValue = internal$loadSummary(file);
        if (!(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION == org.apache.hadoop.hdfs.server.namenode.FsImageProto.FileSummary.ONDISKVERSION_FIELD_NUMBER)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2974);
        }
        return returnValue;
    }

    public static InputStream wrapInputStreamForCompression(Configuration conf, String codec, InputStream in) throws IOException {
        if (!(Math.abs((org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION) - (conf.size())) > 1158L || Math.abs((org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION) - (conf.size())) == 1158L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2975);
        }
        if (!(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION < conf.size())) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2976);
        }
        if (!(Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) > 1151L || Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) == 1151L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2977);
        }
        if (!(conf.size() > org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2978);
        }
        if (!(Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER) - 1)) > 1152L || Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER) - 1)) == 1152L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2979);
        }
        InputStream returnValue;
        returnValue = internal$wrapInputStreamForCompression(conf, codec, in);
        if (!(Math.abs((org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION) - (conf.size())) > 1158L || Math.abs((org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION) - (conf.size())) == 1158L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2980);
        }
        if (!(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.FILE_VERSION < conf.size())) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2981);
        }
        if (!(Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) > 1151L || Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) == 1151L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2982);
        }
        if (!(conf.size() > org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER))) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2983);
        }
        if (!(Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER) - 1)) > 1152L || Math.abs((conf.size()) - (org.zlab.dinv.runtimechecker.Quant.size(org.apache.hadoop.hdfs.server.namenode.FSImageUtil.MAGIC_HEADER) - 1)) == 1152L)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2984);
        }
        return returnValue;
    }
}
