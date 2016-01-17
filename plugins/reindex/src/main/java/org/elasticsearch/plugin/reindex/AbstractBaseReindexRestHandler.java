/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.reindex;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.NoopActionListener;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;

import java.io.IOException;

public abstract class AbstractBaseReindexRestHandler<Request extends ActionRequest<Request>, Response extends BulkIndexByScrollResponse, TA extends TransportAction<Request, Response>>
        extends BaseRestHandler {
    protected final IndicesQueriesRegistry indicesQueriesRegistry;
    private final ClusterService clusterService;
    private final TA action;

    protected AbstractBaseReindexRestHandler(Settings settings, RestController controller, Client client,
            IndicesQueriesRegistry indicesQueriesRegistry, ClusterService clusterService, TA action) {
        super(settings, controller, client);
        this.indicesQueriesRegistry = indicesQueriesRegistry;
        this.clusterService = clusterService;
        this.action = action;
    }

    protected void execute(RestRequest request, Request internalRequest, RestChannel channel) throws IOException {
        if (request.paramAsBoolean("wait_for_completion", false)) {
            action.execute(internalRequest, new BulkIndexByScrollResponseContentListener<Response>(channel));
            return;
        }
        /*
         * Lets try and validate before forking launching the task so we can
         * return errors even if we aren't waiting.
         */
        ActionRequestValidationException validationException = internalRequest.validate();
        if (validationException != null) {
            channel.sendResponse(new BytesRestResponse(channel, validationException));
            return;
        }
        Task task = action.execute(internalRequest, NoopActionListener.instance());
        sendTask(channel, task);
    }

    private void sendTask(RestChannel channel, Task task) throws IOException {
        XContentBuilder builder = channel.newBuilder();
        builder.startObject();
        builder.startObject("task");
        builder.field("node", clusterService.localNode().getId());
        builder.field("id", task.getId());
        builder.endObject();
        builder.endObject();
        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
    }
}
