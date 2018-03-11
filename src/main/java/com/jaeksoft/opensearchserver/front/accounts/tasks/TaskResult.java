/*
 * Copyright 2017-2018 Emmanuel Keller / Jaeksoft
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jaeksoft.opensearchserver.front.accounts.tasks;

import com.jaeksoft.opensearchserver.model.CrawlTaskDefinition;
import com.jaeksoft.opensearchserver.model.TaskDefinition;
import com.jaeksoft.opensearchserver.model.TaskRecord;
import com.jaeksoft.opensearchserver.model.WebCrawlRecord;
import com.jaeksoft.opensearchserver.model.WebCrawlTaskDefinition;
import com.jaeksoft.opensearchserver.services.IndexesService;
import com.jaeksoft.opensearchserver.services.WebCrawlsService;
import com.qwazr.utils.ExceptionUtils;
import com.qwazr.utils.LinkUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskResult {

	private final TaskRecord taskRecord;

	private final String indexName;

	private final String indexPath;

	private final String crawlName;

	private final String crawlPath;

	private TaskResult(final TaskRecord taskRecord, final String indexName, final String indexPath,
			final String crawlName, final String crawlPath) {
		this.taskRecord = taskRecord;
		this.indexPath = indexPath;
		this.indexName = indexName;
		this.crawlName = crawlName;
		this.crawlPath = crawlPath;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getIndexPath() {
		return indexPath;
	}

	public String getCrawlName() {
		return crawlName;
	}

	public String getCrawlPath() {
		return crawlPath;
	}

	public TaskRecord getRecord() {
		return taskRecord;
	}

	public static Builder of(final IndexesService indexesService, final UUID accountId,
			final WebCrawlsService webCrawlsService) {
		return new Builder(indexesService, accountId, webCrawlsService);
	}

	public static class Builder {

		private final Map<UUID, String> indexNameResolver;
		private final Map<String, String> crawlResolver;
		private final WebCrawlsService webCrawlsService;
		private final UUID accountUuid;

		private volatile List<TaskResult> results;

		Builder(final IndexesService indexesService, final UUID accountUuid, final WebCrawlsService webCrawlsService) {
			this.accountUuid = accountUuid;
			indexNameResolver = indexesService.getIndexNameResolver(accountUuid.toString());
			if (webCrawlsService != null) {
				this.webCrawlsService = webCrawlsService;
				this.crawlResolver = new HashMap<>();
			} else {
				this.webCrawlsService = null;
				this.crawlResolver = null;
			}
		}

		public void add(final TaskRecord taskRecord) {
			if (results == null)
				results = new ArrayList<>();
			String indexName = null;
			String indexPath = null;
			String crawlName = null;
			String crawlPath = null;
			final TaskDefinition taskDefinition = taskRecord.getDefinition();
			if (taskDefinition instanceof CrawlTaskDefinition) {
				final CrawlTaskDefinition crawlTask = (CrawlTaskDefinition) taskDefinition;
				indexName = indexNameResolver.get(crawlTask.indexUuid);
				final String accountId = accountUuid.toString();
				if (indexName == null)
					indexName = crawlTask.indexUuid.toString();
				else
					indexPath = "/accounts/" + LinkUtils.urlEncode(accountId) + "/indexes/" +
							LinkUtils.urlEncode(indexName);
				if (taskDefinition instanceof WebCrawlTaskDefinition) {
					if (webCrawlsService != null && crawlResolver != null) {
						crawlName = crawlResolver.computeIfAbsent(taskDefinition.getTaskId(), taskId -> {
							final WebCrawlRecord webCrawlRecord =
									ExceptionUtils.bypass(() -> webCrawlsService.read(accountUuid, crawlTask.id));
							return webCrawlRecord == null ? crawlTask.id.toString() : webCrawlRecord.getName();
						});
						crawlPath = "/accounts/" + LinkUtils.urlEncode(accountId) + "/crawlers/web/" +
								crawlTask.id.toString();
					}
				}
			}
			results.add(new TaskResult(taskRecord, indexName, indexPath, crawlName, crawlPath));
		}

		public List<TaskResult> build() {
			return results == null ? Collections.emptyList() : results;
		}
	}
}
