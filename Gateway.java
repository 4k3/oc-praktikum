package de.uniluebeck.itm.icontrol.communication;

/* ----------------------------------------------------------------------
 * This file is part of the WISEBED project.
 * Copyright (C) 2009 by the Institute of Telematics, University of Luebeck
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the BSD License. Refer to bsd-licence.txt
 * file in the root of the source tree for further details.
------------------------------------------------------------------------*/

/**
 * @class Gateway
 * @author Moehlmann, Schneider, Witt
 * @brief Is responsible for the communication with the robots/sensors
 * @detailed This class is for sending and receiving messages, also to decode and encode the messages
 */

import ishell.device.MessagePacket;
import ishell.plugins.Plugin;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import de.uniluebeck.itm.icontrol.communication.listener.FeatureListener;
import de.uniluebeck.itm.icontrol.communication.listener.MessageListener;

public class Gateway implements Communication {
	private final String stringSeparator = "\r"; // String-value which is used
													// as separator

	private Set<FeatureListener> featureListener;
	private Set<MessageListener> messageListener;
	private Plugin ishellPlugin;
	private final int sendType = 10; 

	public Gateway(Plugin ishellPlugin) {
		this.ishellPlugin = ishellPlugin;
		featureListener = new HashSet<FeatureListener>();
		messageListener = new HashSet<MessageListener>();
	}

	public void addFeatureListener(FeatureListener listener) {
		featureListener.add(listener);
	}

	public void addMessageListener(MessageListener listener) {
		messageListener.add(listener);
	}

	public void removeFeatureListener(FeatureListener listener) {
		featureListener.remove(listener);
	}

	public void removeMessageListener(MessageListener listener) {
		messageListener.remove(listener);
	}

	/**
	 * Sends a task to the sensor-network
	 * 
	 * @param robotId
	 *            The ID of the robot, which is supposed to receive the message
	 *            (integer value must not be higher than 2 bytes)
	 * @param taskName
	 *            The name of the task, which should be proceeded
	 * @param paramLength
	 *            Amount of values in the parameters array(integer value must
	 *            not be higher than 2 bytes)
	 * @param parameters
	 *            The values of the parameters (integer values must not be
	 *            higher than 2 bytes)
	 */
	public void doTask(int robotId, String taskName, int paramLength,
			int[] parameters) {
		byte[] idArray = intToByteArray(robotId, 2);
		byte[] taskNameArray = taskName.getBytes();
		byte paramLengthByte = intToByte(paramLength);
		byte[] parametersArray = new byte[parameters.length];
		for (int i = 0; i < parametersArray.length; i++) {
			parametersArray[i] = intToByte(parameters[i]);
		}
		ByteBuffer bb = ByteBuffer.allocate(2 + idArray.length
				+ parameters.length + taskNameArray.length);
		byte messageType = 0;
		bb.put(messageType);
		bb.put(idArray);
		bb.put(paramLengthByte);
		bb.put(parametersArray);
		bb.put(taskNameArray);
		ishellPlugin.sendPacket(sendType, bb.array());
	}

	/**
	 * Calls every robot, to send the list of available features
	 */
	public void showMeWhatYouGot() {
		byte[] send = new byte[1];
		send[0] = 1;
		ishellPlugin.sendPacket(sendType, send);
	}

	/**
	 * Is called, when a message was received. This methods checks the type and
	 * calls the according method It reads the first byte which should be the
	 * type byte
	 * 
	 * @param message
	 *            The message, which was received
	 */
	public void receive(MessagePacket packet) {
		if (packet.getType() == sendType) {
			byte[] message = packet.getContent();
			int type = unsignedSingleByteToInt(message[0]);
			switch (type) {
			case (2):
				onMessage(cutByteArray(message, 1, message.length));
				break;
			case (3):
				onFeature(cutByteArray(message, 1, message.length));
				break;
			default:
			}
		}
	}

	/**
	 * If a normal message of the robots is received, this message will be
	 * called It decodes the message and informs the observers
	 * 
	 * The first two byte should be the robot ID Followed by the length of the
	 * value array (1 byte) After that the array with the values, of the
	 * previous given length and 2 bytes for each value Finally the rest of the
	 * bytes for the name of the task
	 * 
	 * @param message
	 *            The message, which was received
	 */
	private void onMessage(byte[] message) {
		// Decodes the robot ID
		int robotId = unsignedDualByteToInt(cutByteArray(message, 0, 2));
		// Decodes the amount of parameters
		byte[] lengthByteArray = cutByteArray(message, 2, 3);
		int valueLength = unsignedSingleByteToInt(lengthByteArray[0]);
		// Decodes the parameter array
		byte[] valuesByteArray = cutByteArray(message, 3, valueLength + 3);
		int[] values = new int[valuesByteArray.length];
		for (int i = 0; i < values.length; i++) {
			values[i] = unsignedDualByteToInt(cutByteArray(valuesByteArray,
					i * 2, i * 2 + 1));
		}
		// Decodes the name of the task
		String taskName = new String(cutByteArray(message, valueLength + 3,
				message.length));
		// Calls the observers
		for (MessageListener listener : messageListener) {
			listener.onMessage(robotId, taskName, valueLength, values);
		}

	}

	/**
	 * Is called when a feature list is received. The method decodes the
	 * features and informs the observers
	 * 
	 * @param message
	 *            The message, which was received
	 */
	private void onFeature(byte[] message) {
		// Decodes the robot ID
		int robotId = unsignedDualByteToInt(cutByteArray(message, 0, 2));
		// Decodes the length of the task list
		byte[] taskListLengthArray = cutByteArray(message, 2, 3);
		int taskListLength = unsignedSingleByteToInt(taskListLengthArray[0]);
		// Decodes the amount of parameters for each task
		byte[] paramListLengthArray = cutByteArray(message, 3,
				taskListLength + 3);
		int[] paramListLength = new int[paramListLengthArray.length];
		for (int i = 0; i < paramListLength.length; i++) {
			paramListLength[i] = unsignedSingleByteToInt(paramListLengthArray[i]);
		}
		// Decodes the name of the tasks and the name of parameters
		String tasksParams = new String(cutByteArray(message,
				taskListLength + 3, message.length));
		StringTokenizer st = new StringTokenizer(tasksParams, stringSeparator);
		String[] taskList = new String[taskListLength];
		for (int i = 0; i < taskListLength; i++) {
			taskList[i] = st.nextToken();
		}
		String[][] paramList = new String[taskListLength][maxValueOfArray(paramListLength)];
		for (int i = 0; i < taskListLength; i++) {
			for (int j = 0; j < paramListLength[i]; j++) {
				paramList[i][j] = st.nextToken();
			}
		}
		for (FeatureListener listener : featureListener) {
			listener.onAction(robotId, taskListLength, taskList,
					paramListLength, paramList);
		}
	}

	/**
	 * Converts an integer value into a byte array of a given size
	 * 
	 * @param integer
	 *            The integer value which should be converted
	 * @param byteLength
	 *            The wished size of the byte array
	 * @return The byte array with the converted integer value
	 */
	private byte[] intToByteArray(int integer, int byteLength) {
		int byteNum = (40 - Integer.numberOfLeadingZeros(integer < 0 ? ~integer
				: integer)) / 8;
		byte[] byteArray = new byte[byteLength];
		if (byteNum > byteLength) {
			return null;
		}
		for (int n = 0; n < byteNum; n++)
			byteArray[(byteLength - 1) - n] = (byte) (integer >>> (n * 8));

		return (byteArray);
	}

	/**
	 * Converts an integer value into a single byte
	 * 
	 * @param integer
	 *            The integer value which should be converted
	 * @return The according byte value
	 */
	private byte intToByte(int integer) {
		byte[] array = intToByteArray(integer, 1);
		return array[0];
	}

	/**
	 * Converts a 2 dimensional byte array into an unsigned integer value
	 * 
	 * @param byteArray
	 *            The byte array, which should be converted
	 * @return The unsigned integer value
	 */
	private int unsignedDualByteToInt(byte[] byteArray) {
		int i = 0;
		i |= byteArray[0] & 0xFF;
		i <<= 8;
		i |= byteArray[1] & 0xFF;
		return i;
	}

	/**
	 * Converts a single byte into an unsigned integer value
	 * 
	 * @param unsignedByte
	 *            The byte, which should be converted
	 * @return The unsigned integer value
	 */
	private int unsignedSingleByteToInt(byte unsignedByte) {
		int i = 0;
		i |= unsignedByte & 0xFF;
		return i;
	}

	/**
	 * Cuts a byte array from a given start- and endpoint
	 * 
	 * @param array
	 *            The original byte array
	 * @param start
	 *            The startpoint
	 * @param end
	 *            The endpoint
	 * @return The stripped-down byte array
	 */
	private byte[] cutByteArray(byte[] array, int start, int end) {
		if (end < start) {
			return null;
		}
		byte[] strippedDown = new byte[end - start];
		for (int i = start; i < end; i++) {
			strippedDown[i - start] = array[i];
		}
		return strippedDown;
	}

	/**
	 * Looks for the highest value in an integer array
	 * 
	 * @param array
	 *            The integer array which is supposed to be browsed
	 * @return The highest value of the array
	 */
	private int maxValueOfArray(int[] array) {
		int max = 0;
		for (int i = 0; i < array.length; i++) {
			if (array[i] > max) {
				max = array[i];
			}
		}
		return max;
	}

}
