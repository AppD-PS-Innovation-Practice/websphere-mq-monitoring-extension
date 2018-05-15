# AppDynamics Monitoring Extension for use with IBM WebSphere MQ

## Use case
Websphere MQ, formerly known as MQ (message queue) series, is an IBM standard for program-to-program messaging across multiple platforms. 

The WebSphere MQ monitoring extension can monitor multiple queues managers and their resources, namely queues, topics, channels and listeners.

The metrics for queue manager, queue, topic, channel and listener can be configured.

The MQ Monitor currently supports IBM Websphere MQ version 7.x, 8.x and 9.x.
 
## Prerequisites
In order to use this extension, you do need a [Standalone JAVA Machine Agent](https://docs.appdynamics.com/display/PRO44/Standalone+Machine+Agents) or [SIM Agent](https://docs.appdynamics.com/display/PRO44/Server+Visibility).  For more details on downloading these products, please  visit https://download.appdynamics.com/.

If this extension is configured for **CLIENT** transport type
1. Please make sure the MQ's host and port is accessible. 
2. Credentials of user with correct access rights would be needed in config.yml [(more on that later)](https://github.com/Appdynamics/websphere-mq-monitoring-extension#access-permissions).
3. If the hosting OS for IBM MQ is Windows, Windows user credentials will be needed.  

### Dependencies  
The monitor has a dependency on the following seven JAR files from the IBM MQ distribution:
``` 
com.ibm.mq.commonservices.jar
com.ibm.mq.jar
com.ibm.mq.jmqi.jar
dhbcore.jar
com.ibm.mq.headers.jar
connector.jar
com.ibm.mq.pcf.jar
```
In newer versions of the MQ, IBM has removed **connector.jar** & **dhbcore.jar** and merged its contents in **com.ibm.mq.allclient.jar**.
These jar files are typically found in ```/opt/mqm/java/lib``` on a UNIX server but may be found in an alternate location depending upon your environment. In case **CLIENT** transport type, IBM MQ Client must be installed to get the MQ jars. To download IBM MQ Client jars, see [here](https://www-01.ibm.com/software/integration/wmq/clients/)

## Installation
1. To build from source, clone this repository and run `mvn clean install` from websphere-mq-monitoring-extension directory. This will produce a WMQMonitor-<version>.zip in target directory. Alternatively download the latest release archive from [here](https://github.com/Appdynamics/websphere-mq-monitoring-extension/releases).
2. Unzip contents of WMQMonitor-<version>.zip file and copy to <code><machine-agent-dir>/monitors</code> directory. Do not place the extension in the "extensions" directory of your Machine Agent installation directory.
3. There are two transport modes in which this extension can be run
   * **Binding** : Requires WMQ Extension to be deployed in machine agent on the same machine where WMQ server is installed.  
   * **Client** : In this mode, the WMQ extension is installed on a different host than the IBM MQ server. Please install the [IBM MQ Client](https://www-01.ibm.com/software/integration/wmq/clients/) for this mode to get the necessary jars as mentioned previously. 
4. Edit the classpath element in WMQMonitor/monitor.xml with the absolute path to the required jar files.
   ```
    <classpath>websphere-mq-monitoring-extension.jar;/opt/mqm/java/lib/com.ibm.mq.commonservices.jar;/opt/mqm/java/lib/com.ibm.mq.jar;/opt/mqm/java/lib/com.ibm.mq.jmqi.jar;/opt/mqm/java/lib/com.ibm.mq.headers.jar;/opt/mqm/java/lib/com.ibm.mq.pcf.jar;/opt/mqm/java/lib/com.ibm.mq.allclient.jar</classpath>
   ```
5. If you plan to use **Client** transport type, create a channel of type server connection in each of the queue manager you wish to query. 
6. Edit the config.yml file.  An example config.yml file follows these installation instructions.
7. Restart the Machine Agent.

## Configuration
**Note** : Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a [yaml validator](http://yamllint.com/)
Configure the monitor by editing the config.yml file in <code><machine-agent-dir>/monitors/WMQMonitor/</code>.
1. Configure the metricPrefix with the `<TIER_ID` under which this extension metrics need to be reported. For example
   ```
    metricPrefix: "Server|Component:100|Custom Metrics|WebsphereMQ|"
   ```  
2. Each queueManager requires 5 threads to fetch its metrics concurrently and 1 main thread to run the extension. So if for example, there are 2 queueManagers configured, please set the numberOfThreads to be 11 (2*5+1)
   ```
    numberOfThreads: 11
   ```
3. Configure the queueManages with appropriate fields and filters. Below sample consists of 2 queueManagers. 
   ```
    queueManagers:
      - host: "192.168.57.104"
        port: 1414
        #Actual name of the queue manager
        name: "TEST_QM_1"
        #Channel name of the queue manager
        channelName: "SYSTEM.ADMIN.SVRCONN"
        #The transport type for the queue manager connection, the default is "Bindings" for a binding type connection
        #For bindings type connection WMQ extension (i.e machine agent) need to be on the same machine on which WebbsphereMQ server is running
        #for client type connection change it to "Client".
        transportType: "Client"
        #user with admin level access, no need to provide credentials in case of bindings transport type, it is only applicable for client type
        username: "hello"
        password: "hello"

        #This is the timeout on queue metrics threads.Default value is 20 seconds. No need to change the default
        #Unless you know what you are doing.
        queueMetricsCollectionTimeoutInSeconds: 20
        
        queueFilters:
            #An asterisk on its own matches all possible names.
            include: ["*"]
            #exclude all queues that starts with SYSTEM or AMQ.
            exclude:
               - type: "STARTSWITH"
                 values: ["SYSTEM","AMQ"]

        channelFilters:
            #An asterisk on its own matches all possible names.
            include: ["*"]
            #exclude all queues that starts with SYSTEM.
            exclude:
               - type: "STARTSWITH"
                 values: ["SYSTEM"]
        listenerFilters:
            #Can provide complete channel name or generic names. A generic name is a character string followed by an asterisk (*),
            #for example ABC*, and it selects all objects having names that start with the selected character string.
            #An asterisk on its own matches all possible names.
            include: ["*"]
            exclude:
               #type value: STARTSWITH, EQUALS, ENDSWITH, CONTAINS
               - type: "STARTSWITH"
               #The name of the queue or queue name pattern as per queue filter, comma separated values
                 values: ["SYSTEM"]
    
        topicFilters:
            # For topics, IBM MQ uses the topic wildcard characters ('#' and '+') and does not treat a trailing asterisk as a wildcard
            # https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.pla.doc/q005020_.htm
            include: ["#"]
            exclude:
                 #type value: STARTSWITH, EQUALS, ENDSWITH, CONTAINS
               - type: "STARTSWITH"
                 #The name of the queue or queue name pattern as per queue filter, comma separated values
                 values: ["SYSTEM","$SYS"]

      - host: "102.138.37.105"
        port: 1414
        #Actual name of the queue manager
        name: "TEST_QM_2"
        #Channel name of the queue manager
        channelName: "SYSTEM.ADMIN.SVRCONN"
        #The transport type for the queue manager connection, the default is "Bindings" for a binding type connection
        #For bindings type connection WMQ extension (i.e machine agent) need to be on the same machine on which WebbsphereMQ server is running
        #for client type connection change it to "Client".
        transportType: "Client"
        #user with admin level access, no need to provide credentials in case of bindings transport type, it is only applicable for client type
        username: "hello"
        password: "hello"
        #This is the timeout on queue metrics threads.Default value is 20 seconds. No need to change the default
        #Unless you know what you are doing.
        queueMetricsCollectionTimeoutInSeconds: 20

        queueFilters:
            #Matches all queues  that starts with TACA..
            include: ["TACA*"]
            #exclude all queues that starts with SYSTEM or AMQ.
            exclude:
               - type: "STARTSWITH"
                 values: ["SYSTEM","AMQ"]

        channelFilters:
            #An asterisk on its own matches all possible names.
            include: ["*"]
            #exclude all queues that starts with SYSTEM.
            exclude:
               - type: "STARTSWITH"
                 values: ["SYSTEM"]
    ```
4. The below metrics are configured by default. Metrics that are not required can be commented out or deleted.
    ```
    mqMetrics:
      # This Object will extract queue manager metrics
      - metricsType: "queueMgrMetrics"
        metrics:
          include:
            - Status:
                alias: "Status"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACF_Q_MGR_STATUS"
          
      # This Object will extract queue metrics
      - metricsType: "queueMetrics"
        metrics:
          include:
            - MaxQueueDepth:
                alias: "Max Queue Depth"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_MAX_Q_DEPTH"
                ibmCommand: "MQCMD_INQUIRE_Q"
              
            - CurrentQueueDepth:
                alias: "Current Queue Depth"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_CURRENT_Q_DEPTH"
                ibmCommand: "MQCMD_INQUIRE_Q"
              
            - OpenInputCount:
                alias: "Open Input Count"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_OPEN_INPUT_COUNT"
                ibmCommand: "MQCMD_INQUIRE_Q"
              
            - OpenOutputCount:
                alias: "Open Output Count"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_OPEN_OUTPUT_COUNT"
                ibmCommand: "MQCMD_INQUIRE_Q"
    
            - OldestMsgAge:
                alias: "OldestMsgAge"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACF_OLDEST_MSG_AGE"
                ibmCommand: "MQCMD_INQUIRE_Q_STATUS"
    
            - OnQTime:
                alias: "OnQTime"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACF_Q_TIME_INDICATOR"
                ibmCommand: "MQCMD_INQUIRE_Q_STATUS"
    
            - UncommittedMsgs:
                alias: "UncommittedMsgs"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACF_UNCOMMITTED_MSGS"
                ibmCommand: "MQCMD_INQUIRE_Q_STATUS"
    
            - HighQDepth:
                alias: "HighQDepth"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_HIGH_Q_DEPTH"
                ibmCommand: "MQCMD_RESET_Q_STATS"
    
            - MsgDeqCount:
                alias: "MsgDeqCount"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_MSG_DEQ_COUNT"
                ibmCommand: "MQCMD_RESET_Q_STATS"
    
            - MsgEnqCount:
                alias: "MsgEnqCount"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_MSG_ENQ_COUNT"
                ibmCommand: "MQCMD_RESET_Q_STATS"
    
          
      # This Object will extract channel metrics
      - metricsType: "channelMetrics"
        metrics:
          include:
            - Messages:
                alias: "Messages"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_MSGS"
              
            - Status:
                alias: "Status"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_CHANNEL_STATUS"  #http://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.dev.doc/q090880_.htm
              
            - ByteSent:
                alias: "Byte Sent"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_BYTES_SENT"
              
            - ByteReceived:
                alias: "Byte Received"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_BYTES_RECEIVED"
              
            - BuffersSent:
                alias: "Buffers Sent"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_BUFFERS_SENT"
              
            - BuffersReceived:
                alias: "Buffers Received"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_BUFFERS_RECEIVED"
    
    
      - metricsType: "listenerMetrics"
        metrics:
          include:
            - Status:
                alias: "Status"
                ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACH_LISTENER_STATUS"
    
      # This Object will extract topic metrics
      - metricsType: "topicMetrics"
        metrics:
          include:
            - PublishCount:
                alias: "Publish Count"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_PUB_COUNT"
                ibmCommand: "MQCMD_INQUIRE_TOPIC_STATUS"
            - SubscriptionCount:
                alias: "Subscription Count"
                ibmConstant: "com.ibm.mq.constants.CMQC.MQIA_SUB_COUNT"
                ibmCommand: "MQCMD_INQUIRE_TOPIC_STATUS"
   ```

### Extension Working - Internals
This extension extracts metrics through [PCF framework](https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.adm.doc/q019990_.htm). A complete list of PCF commands are listed [here] (https://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q086870_.htm)
Each queue manager has an administration queue with a standard queue name and the extension sends PCF command messages to that queue. On Windows and Unix platforms, the PCF commands are sent is always sent to the SYSTEM.ADMIN.COMMAND.QUEUE queue. 
More details about that is mentioned [here](https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.adm.doc/q020010_.htm)

By default, the PCF responses are sent to the SYSTEM.DEFAULT.MODEL.QUEUE. Using this queue causes a temporary dynamic queue to be created. You can override the default here by using the `modelQueueName` and `replyQueuePrefix` fields in the config.yml.
More details mentioned [here](https://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q083240_.htm)

### Access Permissions
If you are in **Bindings** mode, please make sure to start the MA process under a user which has permissions to inquire,get,put (since PCF responses cause dynamic queues to be created) on the broker. Similarly, for **Client** mode provide the credentials which have enough access permissions.

### SSL Support
Configure the IBM SSL Cipher Suite in the config.yaml. 

Note that, to use some CipherSuites the unrestricted policy needs to be configured in JRE. Please visit [this link](http://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.security.component.80.doc/security-component/sdkpolicyfiles.html
) for more details. For Oracle JRE, please update with [JCE Unlimited Strength Jurisdiction Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html)

To configure SSL, the MA's trust store and keystore needs to be setup with the JKS filepath. 

Please add the following JVM arguments to the MA start up command or script. 

```-Dcom.ibm.mq.cfg.useIBMCipherMappings=false```  (If you are using IBM Cipher Suites, set the flag to true. Please visit [this link](http://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.dev.doc/q113210_.htm) for more details.
)

```-Djavax.net.ssl.trustStore=<PATH_TO_JKS_FILE>```
```-Djavax.net.ssl.trustStorePassword=<PASS>```
```-Djavax.net.ssl.keyStore=<PATH_TO_JKS_FILE>```
```-Djavax.net.ssl.keyStorePassword=<PASS>```

## Metrics
The metrics will be reported under the tree ```Application Infrastructure Performance|$TIER|Custom Metrics|WebsphereMQ```

### [QueueManagerMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087850_.htm)

<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> Status </td>
<td class='confluenceTd'> 1 - starting, 2 - running, 3 - quiescing </td>
</tr>
</tbody>
</table>

### QueueMetrics

#### [QueueMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087810_.htm)
<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> MaxQueueDepth </td>
<td class='confluenceTd'> Maximum queue depth </td>
</tr>
<tr>
<td class='confluenceTd'> CurrentQueueDepth </td>
<td class='confluenceTd'> Current queue depth </td>
</tr>
<tr>
<td class='confluenceTd'> OpenInputCount </td>
<td class='confluenceTd'> Number of MQOPEN calls that have the queue open for input </td>
</tr>
<tr>
<td class='confluenceTd'> OpenOutputCount </td>
<td class='confluenceTd'> Number of MQOPEN calls that have the queue open for output </td>
</tr>
</tbody>
</table>

#### [QueueStatusMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087890_.htm)
<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> OldestMsgAge </td>
<td class='confluenceTd'> Age of the oldest message </td>
</tr>
<tr>
<td class='confluenceTd'> OnQTime </td>
<td class='confluenceTd'> Indicator of the time that messages remain on the queue </td>
</tr>
<tr>
<td class='confluenceTd'> UncommittedMsgs </td>
<td class='confluenceTd'> The number of uncommitted changes (puts and gets) pending for the queue </td>
</tr>
</tbody>
</table>

#### [ResetQueueStatistics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q088310_.htm)
<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> HighQDepth </td>
<td class='confluenceTd'> Maximum number of messages on a queue </td>
</tr>
<tr>
<td class='confluenceTd'> MsgDeqCount </td>
<td class='confluenceTd'> Number of messages dequeued </td>
</tr>
<tr>
<td class='confluenceTd'> MsgEnqCount </td>
<td class='confluenceTd'> Number of messages enqueued </td>
</tr>
</tbody>
</table>

### [ChannelMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087850_.htm)

<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> Messages </td>
<td class='confluenceTd'> Number of messages sent or received, or number of MQI calls handled </td>
</tr>
<tr>
<td class='confluenceTd'> Status </td>
<td class='confluenceTd'> 1 - binding, 2 - starting, 3 - running, 4 - paused, 5 - stopping, 6 - retrying, 7 - stopped, 8 - requesting, 9 - switching, 10 - initializing </td>
</tr>
<tr>
<td class='confluenceTd'> ByteSent </td>
<td class='confluenceTd'> Number of bytes sent </td>
</tr>
<tr>
<td class='confluenceTd'> ByteReceived </td>
<td class='confluenceTd'> Number of bytes received </td>
</tr>
<tr>
<td class='confluenceTd'> BuffersSent </td>
<td class='confluenceTd'> Number of buffers sent </td>
</tr>
<tr>
<td class='confluenceTd'> BuffersReceived </td>
<td class='confluenceTd'> Number of buffers received </td>
</tr>
</tbody>
</table>

### [ListenerMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087510_.htm)

<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> Status </td>
<td class='confluenceTd'> 1 - starting, 2 - running, 3 - stopping </td>
</tr>
</tbody>
</table>

### [TopicMetrics](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q088150_.htm)

<table><tbody>
<tr>
<th align="left"> Metric Name </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> PublishCount </td>
<td class='confluenceTd'> The number of applications currently publishing to the topic. </td>
</tr>
<tr>
<td class='confluenceTd'> SubscriptionCount </td>
<td class='confluenceTd'> The number of subscribers for this topic string, including durable subscribers who are not currently connected. </td>
</tr>
</tbody>
</table>


## Credentials Encryption
Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

## Troubleshooting
1. Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension. If these don't solve your issue, please follow the last step on the troubleshooting-document to contact the support team.
2. MQ Version incompatibilities :  In case of any jar incompatibility issue, the rule of thumb is to **Use the jars from MQ version 7.5**. We have seen some jar incompatibility issues on IBM version 7.0.x ,version 7.1.x and version 8.x when the extension is configured in **Client** mode. However, after replacing the jars with MQ version 7.5's jars, everything worked fine. 
3. `The config cannot be null` error.
   This usually happenes when on a windows machine in monitor.xml you give config.yaml file path with linux file path separator `/`. Use Windows file path separator `\` e.g. `monitors\MQMonitor\config.yaml` .
4. Error `Completion Code '2', Reason '2495'`
   Normally this error occurs if the environment variables are not set up correctly for this extension to work MQ in Bindings Mode.
   
   If you are seeing `Failed to load the WebSphere MQ native JNI library: 'mqjbnd'`, please add the following jvm argument when starting the MA. 
   
   -Djava.library.path=<path to lib64 directory> For eg. on Unix it could -Djava.library.path=/opt/mqm/java/lib64 
   
   Sometimes you also have run the setmqenv script before using the above jvm argument to start the machine agent. 
   
   . /opt/mqm/bin/setmqenv -s 
   
   For more details, please check this [doc](https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_7.1.0/com.ibm.mq.doc/zr00610_.htm)
   
   This might occour due to various reasons ranging from incorrect installation to applying [ibm fix packs](http://www-01.ibm.com/support/docview.wss?uid=swg21410038) but most of the time it happens when you are trying to connect in `Bindings` mode and machine agent is not on the same machine on which WMQ server is running. If you want to connect to WMQ server from a remote machine then connect using `Client` mode.
   
   Another way to get around this issue is to avoid using the Bindings mode. Connect using CLIENT transport type from a remote box. Make sure to provide Windows admin username and password in the config.yaml.

5. Error `Completion Code '2', Reason '2035'`
   This could happen for various reasons but for most of the cases, for **Client** mode the user specified in config.yaml is not authorized to access the queue manager. Also sometimes even if userid and password are correct, channel auth (CHLAUTH) for that queue manager blocks traffics from other ips, you need to contact admin to provide you access to the queue manager.
   For Bindings mode, please make sure that the MA is owned by a mqm user. Please check [this doc](https://www-01.ibm.com/support/docview.wss?uid=swg21636093) 
  
6. MQJE001: Completion Code '2', Reason '2195'
   This could happen in **Client** mode. Please make sure that the IBM MQ dependency jars are correctly referenced in classpath of monitor.xml, copying jars to a different directory is not recommended by IBM. Another way this could be fixed is to use 7.5.2 version of the jars. 

7. MQJE001: Completion Code '2', Reason '2400'
   This could happen if unsupported cipherSuite is provided or JRE not having/enabled unlimited jurisdiction policy files. Please check SSL Support section.

## Support Tickets
If after going through the Troubleshooting Document you have not been able to get your extension working, please file a ticket and add the following information.

Please provide the following in order for us to assist you better.  

1. Stop the running machine agent .
2. Delete all existing logs under <MachineAgent>/logs .
3. Please enable debug logging by editing the file <MachineAgent>/conf/logging/log4j.xml. Change the level value of the following <logger> elements to debug. 
     <logger name="com.singularity">
     <logger name="com.appdynamics">
4. Start the machine agent and please let it run for 10 mins. Then zip and upload all the logs in the directory <MachineAgent>/logs/*.
5. Attach the zipped <MachineAgent>/conf/* directory here.
 6. Attach the zipped <MachineAgent>/monitors/ExtensionFolderYouAreHavingIssuesWith directory here .

For any support related questions, you can also contact help@appdynamics.com.

## Contributing
Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/websphere-mq-monitoring-extension).

## Version
|          Name            |  Version                |
|--------------------------|-------------------------|
|Extension Version         |7.0                      |
|Controller Compatibility  |4.0 +                    |
|Product Tested On         |IBM MQ 7.x, 8.x, 9.x and Windows, Unix, AIX|
|Last Update               |25th April, 2018         |

List of Changes to this extension can be found [here](https://github.com/Appdynamics/websphere-mq-monitoring-extension/blob/v7.0/CHANGELOG.md)




		
