/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import gherkin.formatter.model.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;
import rp.com.google.common.base.Strings;
import rp.com.google.common.collect.ImmutableMap;

import java.util.*;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";

	//@formatter:off
	private static final Map<String, String> STATUS_MAPPING = ImmutableMap.<String, String>builder()
			.put("passed", Statuses.PASSED)
			.put("skipped", Statuses.SKIPPED)
			.put("pending", Statuses.SKIPPED)
			//TODO replace with NOT_IMPLEMENTED in future
			.put("undefined", Statuses.SKIPPED).build();
	//@formatter:on

	private Utils() {

	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null);
	}

	public static void finishTestItem(Launch rp, Maybe<String> itemId, String status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}

		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setStatus(status);
		rq.setEndTime(Calendar.getInstance().getTime());

		rp.finishTestItem(itemId, rq);

	}

	public static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description, List<Tag> tags,
			String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setName(name);
		rq.setTags(extractTags(tags));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);

		return rp.startTestItem(rootItemId, rq);
	}

	public static void sendLog(final String message, final String level, final File file) {
		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String item) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setMessage(message);
				rq.setTestItemId(item);
				rq.setLevel(level);
				rq.setLogTime(Calendar.getInstance().getTime());
				if (file != null) {
					rq.setFile(file);
				}
				return rq;
			}
		});
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<String> extractTags(List<Tag> tags) {
		Set<String> returnTags = new HashSet<String>();
		for (Tag tag : tags) {
			returnTags.add(tag.getName());
		}
		return returnTags;
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	public static String mapLevel(String cukesStatus) {
		String mapped = null;
		if (cukesStatus.equalsIgnoreCase("passed")) {
			mapped = "INFO";
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			mapped = "WARN";
		} else {
			mapped = "ERROR";
		}
		return mapped;
	}

	/**
	 * Map Cucumber statuses to RP
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular status
	 */
	public static String mapStatus(String cukesStatus) {
		if (Strings.isNullOrEmpty(cukesStatus)) {
			return Statuses.FAILED;
		}
		String status = STATUS_MAPPING.get(cukesStatus.toLowerCase());
		return null == status ? Statuses.FAILED : status;
	}

	/**
	 * Generate statement representation
	 *
	 * @param stmt   - Cucumber statement
	 * @param prefix - substring to be prepended at the beginning (optional)
	 * @param infix  - substring to be inserted between keyword and name
	 * @param suffix - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	public static String buildStatementName(BasicStatement stmt, String prefix, String infix, String suffix) {
		return (prefix == null ? "" : prefix) + stmt.getKeyword() + infix + stmt.getName() + (suffix == null ? "" : suffix);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	public static String buildMultilineArgument(Step step) {
		List<DataTableRow> table = step.getRows();
		DocString ds = step.getDocString();
		StringBuilder marg = new StringBuilder();
		StringBuilder markDownSeparator = new StringBuilder();
		if (table != null) {
			marg.append("\r\n\r\n");
			for (Row row : table) {
				marg.append(TABLE_SEPARATOR);
				for (String cell : row.getCells()) {
					marg.append(" ").append(cell).append(" ").append(TABLE_SEPARATOR);
				}
				marg.append("\r\n");
				if (markDownSeparator.length() == 0) {
					markDownSeparator.append(TABLE_SEPARATOR).append("-").append(TABLE_SEPARATOR);
					marg.append(markDownSeparator);
					marg.append("\r\n");
				}
			}
		}

		if (ds != null) {
			marg.append(DOCSTRING_DECORATOR).append(ds.getValue()).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}
}
