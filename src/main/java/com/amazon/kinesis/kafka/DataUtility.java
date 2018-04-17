package com.amazon.kinesis.kafka;

import java.io.UnsupportedEncodingException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.json.JsonConverter;

import com.amazonaws.services.kinesisfirehose.model.Record;

public class DataUtility {

	/**
	 * Parses Kafka Values
	 *
	 * @param schema
	 *            - Schema of passed message as per
	 *            https://kafka.apache.org/0100/javadoc/org/apache/kafka/connect/data/Schema.html
	 * @param value
	 *            - Value of the message
	 * @return Parsed bytebuffer as per schema type
	 */
	public static ByteBuffer parseValue(Schema schema, Object value) {
		Schema.Type t = schema.type();
		switch (t) {
		case INT8:
			ByteBuffer byteBuffer = ByteBuffer.allocate(1);
			byteBuffer.put((Byte) value);
			return byteBuffer;
		case INT16:
			ByteBuffer shortBuf = ByteBuffer.allocate(2);
			shortBuf.putShort((Short) value);
			return shortBuf;
		case INT32:
			ByteBuffer intBuf = ByteBuffer.allocate(4);
			intBuf.putInt((Integer) value);
			return intBuf;
		case INT64:
			ByteBuffer longBuf = ByteBuffer.allocate(8);
			longBuf.putLong((Long) value);
			return longBuf;
		case FLOAT32:
			ByteBuffer floatBuf = ByteBuffer.allocate(4);
			floatBuf.putFloat((Float) value);
			return floatBuf;
		case FLOAT64:
			ByteBuffer doubleBuf = ByteBuffer.allocate(8);
			doubleBuf.putDouble((Double) value);
			return doubleBuf;
		case BOOLEAN:
			ByteBuffer boolBuffer = ByteBuffer.allocate(1);
			boolBuffer.put((byte) ((Boolean) value ? 1 : 0));
			return boolBuffer;
		case STRING:
			try {
				return ByteBuffer.wrap(((String) value).getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				System.out.println("Message cannot be translated:" + e.getLocalizedMessage());
			}
		case ARRAY:
			Schema sch = schema.valueSchema();
			if (sch.type() == Type.MAP || sch.type() == Type.STRUCT) {
				throw new DataException("Invalid schema type.");
			}
			Object[] objs = (Object[]) value;
			ByteBuffer[] byteBuffers = new ByteBuffer[objs.length];
			int noOfByteBuffer = 0;

			for (Object obj : objs) {
				byteBuffers[noOfByteBuffer++] = parseValue(sch, obj);
			}

			ByteBuffer result = ByteBuffer.allocate(Arrays.stream(byteBuffers).mapToInt(Buffer::remaining).sum());
			Arrays.stream(byteBuffers).forEach(bb -> result.put(bb.duplicate()));
			return result;
		case BYTES:
			if (value instanceof byte[])
				return ByteBuffer.wrap((byte[]) value);
			else if (value instanceof ByteBuffer)
				return (ByteBuffer) value;
		case MAP:
			// TO BE IMPLEMENTED
			return ByteBuffer.wrap(null);
		case STRUCT:
			JsonConverter converter = new JsonConverter();
			Map<String, Object> converterConfig = new HashMap<>();
			converterConfig.put("schemas.enable", "false");
			converterConfig.put("schemas.cache.size", "1000");
			converter.configure(converterConfig, false);

			// Use JsonCoverter to stringify into JSON
			// Since we are only interested into the JSON string of record value,
			// we supply empty string into topic and simply don't care about
			byte[] rawJson = converter.fromConnectData("", schema, value);
			return ByteBuffer.wrap(rawJson);
		}
		return null;
	}

	/**
	 * Converts Kafka record into Kinesis record
	 *
	 * @param sinkRecord
	 *            Kafka unit of message
	 * @return Kinesis unit of message
	 */
	public static Record createRecord(SinkRecord sinkRecord) {
		return new Record().withData(parseValue(sinkRecord.valueSchema(), sinkRecord.value()));
	}

}
