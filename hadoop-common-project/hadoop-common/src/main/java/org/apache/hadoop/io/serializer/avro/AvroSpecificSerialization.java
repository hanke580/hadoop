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
package org.apache.hadoop.io.serializer.avro;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

/**
 * Serialization for Avro Specific classes. This serialization is to be used
 * for classes generated by Avro's 'specific' compiler.
 */
@SuppressWarnings("unchecked")
@InterfaceAudience.Public
@InterfaceStability.Evolving
public class AvroSpecificSerialization extends AvroSerialization<SpecificRecord> {

    @InterfaceAudience.Private
    @Override
    public boolean accept(Class<?> c) {
        return SpecificRecord.class.isAssignableFrom(c);
    }

    @InterfaceAudience.Private
    @Override
    public DatumReader getReader(Class<SpecificRecord> clazz) {
        try {
            return new SpecificDatumReader(clazz.newInstance().getSchema());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @InterfaceAudience.Private
    @Override
    public Schema getSchema(SpecificRecord t) {
        return t.getSchema();
    }

    @InterfaceAudience.Private
    @Override
    public DatumWriter getWriter(Class<SpecificRecord> clazz) {
        return new SpecificDatumWriter();
    }
}
