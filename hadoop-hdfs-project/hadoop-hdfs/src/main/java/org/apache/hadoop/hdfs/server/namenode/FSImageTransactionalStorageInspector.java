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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.common.Storage.StorageDirectory;
import org.apache.hadoop.hdfs.server.namenode.NNStorage.NameNodeDirType;
import org.apache.hadoop.hdfs.server.namenode.NNStorage.NameNodeFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class FSImageTransactionalStorageInspector extends FSImageStorageInspector {

    public static final Log LOG = LogFactory.getLog(FSImageTransactionalStorageInspector.class);

    private boolean needToSave = false;

    private boolean isUpgradeFinalized = true;

    final List<FSImageFile> foundImages = new ArrayList<FSImageFile>();

    private long maxSeenTxId = 0;

    private final List<Pattern> namePatterns = Lists.newArrayList();

    FSImageTransactionalStorageInspector() {
        this(EnumSet.of(NameNodeFile.IMAGE));
    }

    FSImageTransactionalStorageInspector(EnumSet<NameNodeFile> nnfs) {
        for (NameNodeFile nnf : nnfs) {
            Pattern pattern = Pattern.compile(nnf.getName() + "_(\\d+)");
            namePatterns.add(pattern);
        }
    }

    private Matcher internal$matchPattern(String name) {
        for (Pattern p : namePatterns) {
            Matcher m = p.matcher(name);
            if (m.matches()) {
                return m;
            }
        }
        return null;
    }

    public void internal$inspectDirectory(StorageDirectory sd) throws IOException {
        // Was the directory just formatted?
        if (!sd.getVersionFile().exists()) {
            LOG.info("No version file in " + sd.getRoot());
            needToSave |= true;
            return;
        }
        // Check for a seen_txid file, which marks a minimum transaction ID that
        // must be included in our load plan.
        try {
            maxSeenTxId = Math.max(maxSeenTxId, NNStorage.readTransactionIdFile(sd));
        } catch (IOException ioe) {
            LOG.warn("Unable to determine the max transaction ID seen by " + sd, ioe);
            return;
        }
        File currentDir = sd.getCurrentDir();
        File[] filesInStorage;
        try {
            filesInStorage = FileUtil.listFiles(currentDir);
        } catch (IOException ioe) {
            LOG.warn("Unable to inspect storage directory " + currentDir, ioe);
            return;
        }
        for (File f : filesInStorage) {
            LOG.debug("Checking file " + f);
            String name = f.getName();
            // Check for fsimage_*
            Matcher imageMatch = this.matchPattern(name);
            if (imageMatch != null) {
                if (sd.getStorageDirType().isOfType(NameNodeDirType.IMAGE)) {
                    try {
                        long txid = Long.parseLong(imageMatch.group(1));
                        foundImages.add(new FSImageFile(sd, f, txid));
                    } catch (NumberFormatException nfe) {
                        LOG.error("Image file " + f + " has improperly formatted " + "transaction ID");
                        // skip
                    }
                } else {
                    LOG.warn("Found image file at " + f + " but storage directory is " + "not configured to contain images.");
                }
            }
        }
        // set finalized flag
        isUpgradeFinalized = isUpgradeFinalized && !sd.getPreviousDir().exists();
    }

    public boolean internal$isUpgradeFinalized() {
        return isUpgradeFinalized;
    }

    /**
     * @return the image files that have the most recent associated
     * transaction IDs.  If there are multiple storage directories which
     * contain equal images, we'll return them all.
     *
     * @throws FileNotFoundException if not images are found.
     */
    List<FSImageFile> internal$getLatestImages() throws IOException {
        LinkedList<FSImageFile> ret = new LinkedList<FSImageFile>();
        for (FSImageFile img : foundImages) {
            if (ret.isEmpty()) {
                ret.add(img);
            } else {
                FSImageFile cur = ret.getFirst();
                if (cur.txId == img.txId) {
                    ret.add(img);
                } else if (cur.txId < img.txId) {
                    ret.clear();
                    ret.add(img);
                }
            }
        }
        if (ret.isEmpty()) {
            throw new FileNotFoundException("No valid image files found");
        }
        return ret;
    }

    public List<FSImageFile> getFoundImages() {
        return ImmutableList.copyOf(foundImages);
    }

    public boolean internal$needToSave() {
        return needToSave;
    }

    long internal$getMaxSeenTxId() {
        return maxSeenTxId;
    }

    private Matcher matchPattern(String name) {
        Matcher returnValue;
        returnValue = internal$matchPattern(name);
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2924);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2925);
        }
        if (!(this.foundImages.size() == this.maxSeenTxId)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2926);
        }
        return returnValue;
    }

    @Override
    public void inspectDirectory(StorageDirectory sd) throws IOException {
        if (!(this.foundImages.size() == this.maxSeenTxId)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2927);
        }
        internal$inspectDirectory(sd);
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2928);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2929);
        }
    }

    @Override
    public boolean isUpgradeFinalized() {
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2930);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2931);
        }
        boolean returnValue;
        returnValue = internal$isUpgradeFinalized();
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2932);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2933);
        }
        return returnValue;
    }

    /**
     * @return the image files that have the most recent associated
     * transaction IDs.  If there are multiple storage directories which
     * contain equal images, we'll return them all.
     *
     * @throws FileNotFoundException if not images are found.
     */
    @Override
    List<FSImageFile> getLatestImages() throws IOException {
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2934);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2935);
        }
        List<FSImageFile> returnValue;
        returnValue = internal$getLatestImages();
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2936);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2937);
        }
        return returnValue;
    }

    @Override
    public boolean needToSave() {
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2938);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2939);
        }
        boolean returnValue;
        returnValue = internal$needToSave();
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2940);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2941);
        }
        return returnValue;
    }

    @Override
    long getMaxSeenTxId() {
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2942);
        }
        if (!(this.maxSeenTxId == org.zlab.dinv.runtimechecker.Quant.size(this.foundImages) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2943);
        }
        long returnValue;
        returnValue = internal$getMaxSeenTxId();
        if (!(this.foundImages.size() == org.zlab.dinv.runtimechecker.Quant.size(this.namePatterns) - 1)) {
            org.zlab.dinv.runtimechecker.Runtime.addViolation(2944);
        }
        return returnValue;
    }
}
