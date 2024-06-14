/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.ibm.mq.MQException;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
//import com.ibm.mq.constants.CMQXC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.pcf.*;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * This class is responsible for channel metric collection.
 *
 * @author rajeevsingh ,James Schneider
 * @version 2.0
 *
 */
public class ChannelMetricsCollector extends MetricsCollector implements Runnable {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(ChannelMetricsCollector.class);
	private final String artifact = "Channels";

	/*
	 * The Channel Status values are mentioned here http://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.dev.doc/q090880_.htm
	 */

	public ChannelMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
		this.metricsToReport = metricsToReport;
		this.monitorContextConfig = monitorContextConfig;
		this.agent = agent;
		this.metricWriteHelper = metricWriteHelper;
		this.queueManager = queueManager;
		this.countDownLatch = countDownLatch;
	}

	public void run() {
		try {
			this.process();
		} catch (TaskExecutionException e) {
			logger.error("Error in ChannelMetricsCollector ", e);
		} finally {
			countDownLatch.countDown();
		}
	}

	@Override
	protected void publishMetrics() throws TaskExecutionException {
		long entryTime = System.currentTimeMillis();

		if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
			logger.debug("Channel metrics to report from the config is null or empty, nothing to publish");
			return;
		}

		int[] attrs = getIntAttributesArray(CMQCFC.MQCACH_CHANNEL_NAME, CMQCFC.MQCACH_CONNECTION_NAME/*, CMQCFC.MQCACH_LAST_MSG_DATE*/);
		logger.debug("Attributes being sent along PCF agent request to query channel metrics: " + Arrays.toString(attrs));

		Set<String> channelGenericNames = this.queueManager.getChannelFilters().getInclude();



		List<String> activeChannels = Lists.newArrayList();
		for(String channelGenericName : channelGenericNames/*Arrays.asList(channels)*/){
/*			String last_msg_date = "";
			String last_msg_time = "";
			boolean last_msg_date_time = false;*/

			String tz;
			tz = queueManager.getTimeZone()== null?"UTC":queueManager.getTimeZone().trim();
			logger.debug("Timezone is set to {}",tz);


			PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS);
			request.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, channelGenericName);
			request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, CMQC.MQOT_CURRENT_CHANNEL);
			request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_ATTRS, attrs);
			//request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, CMQC.MQOT_SAVED_CHANNEL);


/*			PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_CHANNEL);
			request.addParameter(MQConstants.MQCACH_CHANNEL_NAME, channelGenericName);
			request.addParameter(MQConstants.MQIACH_CHANNEL_TYPE, MQConstants.MQCHT_ALL);
			attrs = getIntAttributesArray(CMQCFC.MQCACH_CHANNEL_NAME, CMQCFC.MQCACH_CONNECTION_NAME);
			//request.addParameter(MQConstants.MQIACH_CHANNEL_INSTANCE_ATTRS, attrs);*/



			try {
				logger.debug("sending PCF agent request to query metrics for generic channel {}", channelGenericName);
				long startTime = System.currentTimeMillis();
				logger.debug("Request: {} channels.",request);
				PCFMessage[] response = agent.send(request);
				logger.debug("Found: {} channels.",response.length);
				logger.debug("Response: {} channels.",response);
				long endTime = System.currentTimeMillis() - startTime;
				logger.debug("PCF agent queue metrics query response for generic queue {} received in {} milliseconds", channelGenericName, endTime);
				if (response == null || response.length <= 0) {
					logger.debug("Unexpected Error while PCFMessage.send(), response is either null or empty");
					return;
				}
				for (int i = 0; i < response.length; i++) {
					String last_msg_date = "";
					String last_msg_time = "";
					boolean last_msg_date_time = false;
					String channelName = response[i].getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
					//List<Metric> metrics = Lists.newArrayList();
					logger.debug("channel {} response:",channelName);
					logger.debug(response[i].toString());
					Set<ExcludeFilters> excludeFilters = this.queueManager.getChannelFilters().getExclude();
					if(!isExcluded(channelName,excludeFilters)) { //check for exclude filters
						logger.debug("Pulling out metrics for channel name {}",channelName);
						Iterator<String> itr = getMetricsToReport().keySet().iterator();
						List<Metric> metrics = Lists.newArrayList();
						while (itr.hasNext()) {
							String metrickey = itr.next();
							WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
							PCFParameter pcfParam = response[i].getParameter(wmqOverride.getConstantValue());
							if (pcfParam != null) {
								if (pcfParam instanceof MQCFIN) {
									int metricVal = response[i].getIntParameterValue(wmqOverride.getConstantValue());
									Metric metric = createMetric(queueManager, metrickey, metricVal, wmqOverride, getAtrifact(), channelName, metrickey);
									metrics.add(metric);
									if ("Status".equals(metrickey)) {
										if (metricVal == 3) {
											activeChannels.add(channelName);
										}
									}
								}
							else if (pcfParam instanceof MQCFST) {
								long metricVal = 0;

								String s_metricVal = response[i].getStringParameterValue(wmqOverride.getConstantValue()).trim();
								if (!s_metricVal.isEmpty()) {
									logger.debug("String value {} for : {} {}", s_metricVal, channelName, wmqOverride.getIbmConstant());

									if (wmqOverride.getConstantValue() == com.ibm.mq.constants.CMQCFC.MQCACH_LAST_MSG_DATE) {
										last_msg_date = s_metricVal;

									} else if (wmqOverride.getConstantValue() == com.ibm.mq.constants.CMQCFC.MQCACH_LAST_MSG_TIME) {
										last_msg_time = s_metricVal;

									}  else {
										logger.debug("Skipping .. {}. Unsupported KPI.", wmqOverride.getConstantValue());
										continue;
									}


									Metric metric;
									logger.debug("OUTSIDE last_msg_date {} last_msg_time {} - last_msg_date_time {}", last_msg_date, last_msg_time, last_msg_date_time);

									if (!last_msg_date.isEmpty() && !last_msg_time.isEmpty() && !last_msg_date_time) {
										logger.debug("INSIDE last_msg_date {} last_msg_time {} - last_msg_date_time {}", last_msg_date, last_msg_time, last_msg_date_time);
										SimpleDateFormat sdtf = new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
										String dateAndTime = last_msg_date+" "+last_msg_time;

										sdtf.setTimeZone(TimeZone.getTimeZone(tz));
										metricVal = 0;
										try {
											metricVal = sdtf.parse(dateAndTime).getTime() / 1000;


										} catch (ParseException e) {
											e.printStackTrace();
										}
										long epoch = System.currentTimeMillis() / 1000;
										long minutes_since_last_msg = (epoch - metricVal)/60;

										logger.debug("minutes_since_last_msg : {}", minutes_since_last_msg);



										metric = createMetric(queueManager, "minutes_since_last_msg", minutes_since_last_msg, null, getAtrifact(), channelName, "minutes_since_last_msg");
										metrics.add(metric);
										last_msg_date_time = true;


									}


								}


							}
							}
							}
						publishMetrics(metrics);
					}
					else{
						logger.debug("Channel name {} is excluded.",channelName);
					}
				}
			}
			catch (PCFException pcfe) {
				String errorMsg = "";
				if (pcfe.getReason() == MQConstants.MQRCCF_CHL_STATUS_NOT_FOUND) {
					errorMsg = "Channel- " + channelGenericName + " :";
					errorMsg += "Could not collect channel information as channel is stopped or inactive: Reason '3065'\n";
					errorMsg += "If the channel type is MQCHT_RECEIVER, MQCHT_SVRCONN or MQCHT_CLUSRCVR, then the only action is to enable the channel, not start it.";
					logger.error(errorMsg,pcfe);
				} else if (pcfe.getReason() == MQConstants.MQRC_SELECTOR_ERROR) {
					logger.error("Invalid metrics passed while collecting channel metrics, check config.yaml: Reason '2067'",pcfe);
				}
			} catch (Exception e) {
				logger.error("Unexpected Error occoured while collecting metrics for channel " + channelGenericName, e);
			}
		}

		logger.info("Active Channels in queueManager {} are {}", WMQUtil.getQueueManagerNameFromConfig(queueManager), activeChannels);
		List<String> uniqActiveChannels = new ArrayList<>(new HashSet<>(activeChannels));
		logger.info("Unique Active Channels in queueManager {} are {}", WMQUtil.getQueueManagerNameFromConfig(queueManager), uniqActiveChannels);


		PCFMessage pcfCmdQueryDetail = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL_NAMES);
		pcfCmdQueryDetail.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "*");
		pcfCmdQueryDetail.addParameter(MQConstants.MQIACH_CHANNEL_TYPE, MQConstants.MQCHT_ALL );



		String[] channels = null;
		List<Metric> metrics = Lists.newArrayList();


		try {
			logger.info("Check channel list" );

			PCFMessage[] pcfResponseDetail = agent.send(pcfCmdQueryDetail);


			logger.debug("Channel List Response: {} channels.", pcfResponseDetail);
			channels = pcfResponseDetail[0].getStringListParameterValue(MQConstants.MQCACH_CHANNEL_NAMES);
			logger.debug("Response: {} channels.",channels);
			Set<ExcludeFilters> excludeFilters = this.queueManager.getChannelFilters().getExclude();
			for(String channelName : Arrays.asList(channels)) {
				if (!isExcluded(channelName.trim(), excludeFilters) && !activeChannels.contains(channelName.trim())) { //check for exclude filters
					logger.debug("Persist status for this channel :  {}", channelName);
					logger.debug("activeChannels :  {}", activeChannels);
					//Metric metric = createMetric(queueManager, "minutes_since_last_put", minutes_since_last_put, null, getAtrifact(), queueName, "minutes_since_last_put");
					Metric metric = createMetric(queueManager, "Status", 0, null, getAtrifact(), channelName, "Status");
					metrics.add(metric);

				}
			}

		} catch (PCFException e) {
			logger.debug("Channel List PCFException: {} ", e);
			//e.printStackTrace();

	} catch (MQException e) {
			logger.debug("Channel List MQException: {} ", e);
			//e.printStackTrace();
		} catch (IOException e) {
			logger.debug("Channel List IOException: {} ", e);
			//e.printStackTrace();
		}finally {
			logger.debug("Channel List finally");
			publishMetrics(metrics);
		}


		//Metric activeChannelsCountMetric = createMetric(queueManager,"ActiveChannelsCount", activeChannels.size(), null, getAtrifact(), "ActiveChannelsCount");
		Metric activeChannelsCountMetric = createMetric(queueManager,"ActiveChannelsCount", uniqActiveChannels.size(), null, getAtrifact(), "ActiveChannelsCount");
		publishMetrics(Arrays.asList(activeChannelsCountMetric));

		long exitTime = System.currentTimeMillis() - entryTime;
		logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);

	}

	public String getAtrifact() {
		return artifact;
	}

	public Map<String, WMQMetricOverride> getMetricsToReport() {
		return this.metricsToReport;
	}
}