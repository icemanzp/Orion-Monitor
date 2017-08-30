/**
 * @Probject Name: servlet-monitor-dev-sql
 * @Path: com.wfj.netty.servlet.handler.factoryKafkaConnectManager.java
 * @Create By Jack
 * @Create In 2016年4月6日 下午3:06:21
 * TODO
 */
package com.jack.netty.servlet.handler.factory;

import java.util.Properties;
import java.util.concurrent.Future;

import com.jack.netty.servlet.conf.EnvPropertyConfig;
import com.jack.netty.servlet.conf.SystemPropertyConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jack.netty.servlet.conf.Constant;

/**
 * kafka 工具类
 * @Class Name KafkaConnectManager
 * @Author Jack
 * @Create In 2016年4月6日
 */
public class KafkaConnectManager {
	
	private static Logger log = LoggerFactory.getLogger(KafkaConnectManager.class);
	private static KafkaProducer<String, String> kp;
	
	private static KafkaProducer<String, String> getProducer() {
		if (kp == null) {
			SystemPropertyConfig.init();
			Properties props = new Properties();
			props.put("bootstrap.servers", SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_BOOTSTRAP_SERVER));
			props.put("acks", SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_ACKS, "1"));
			props.put("retries", SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_RETRIES, "0"));
			props.put("batch.size", SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_BATCH_SIZE, "16384"));
			props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			kp = new KafkaProducer<String, String>(props);
		}
		return kp;
	}

	/**
	 * 发送信息到 Kafka
	 * @Methods Name sendMsgToTopic
	 * @Create In 2016年4月6日 By Jack
	 * @param topic 指定的Topic
	 * @param key   消息的 Key
	 * @param msg   消息内容
	 * @return
	 */
	public static boolean sendMsgToTopic(String topic, final String key, final String msg) {
		Producer<String, String> producer = getProducer();
		boolean result = false;
		
		ProducerRecord<String, String> record = new ProducerRecord<String, String>(topic, key, msg);
		try{
			Future<RecordMetadata> future = producer.send(record);
			log.debug("message send to partition " + future.get().partition() + ", offset: " + future.get().offset());
			result = true;
		}catch(Exception e){
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001018"));
			log.error("Details: " + e.getMessage());
			log.error("Kafka Msg: " + key + ":" + msg);
		}
		return result;
	}
}
