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
package org.apache.hadoop.hdfs.server.federation.store.protocol.impl.pb;

import java.io.IOException;
import org.apache.hadoop.hdfs.federation.protocol.proto.HdfsServerFederationProtos.RemoveMountTableEntryRequestProto;
import org.apache.hadoop.hdfs.federation.protocol.proto.HdfsServerFederationProtos.RemoveMountTableEntryRequestProtoOrBuilder;
import org.apache.hadoop.hdfs.server.federation.store.protocol.RemoveMountTableEntryRequest;
import org.apache.hadoop.hdfs.server.federation.store.records.impl.pb.PBRecord;
import org.apache.hadoop.thirdparty.protobuf.Message;

/**
 * Protobuf implementation of the state store API object
 * RemoveMountTableEntryRequest.
 */
public class RemoveMountTableEntryRequestPBImpl extends RemoveMountTableEntryRequest implements PBRecord {

    private FederationProtocolPBTranslator<RemoveMountTableEntryRequestProto, RemoveMountTableEntryRequestProto.Builder, RemoveMountTableEntryRequestProtoOrBuilder> translator = new FederationProtocolPBTranslator<RemoveMountTableEntryRequestProto, RemoveMountTableEntryRequestProto.Builder, RemoveMountTableEntryRequestProtoOrBuilder>(RemoveMountTableEntryRequestProto.class);

    public RemoveMountTableEntryRequestPBImpl() {
    }

    public RemoveMountTableEntryRequestPBImpl(RemoveMountTableEntryRequestProto proto) {
        this.setProto(proto);
    }

    @Override
    public RemoveMountTableEntryRequestProto getProto() {
        return this.translator.build();
    }

    @Override
    public void setProto(Message proto) {
        this.translator.setProto(proto);
    }

    @Override
    public void readInstance(String base64String) throws IOException {
        this.translator.readInstance(base64String);
    }

    @Override
    public String getSrcPath() {
        return this.translator.getProtoOrBuilder().getSrcPath();
    }

    @Override
    public void setSrcPath(String path) {
        this.translator.getBuilder().setSrcPath(path);
    }
}
