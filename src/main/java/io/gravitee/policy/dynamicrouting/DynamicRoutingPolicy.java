/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.dynamicrouting;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.dynamicrouting.configuration.DynamicRoutingPolicyConfiguration;
import io.gravitee.policy.dynamicrouting.configuration.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DynamicRoutingPolicy {

    /**
     * LOGGER
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicRoutingPolicy.class);

    private final static Pattern GROUP_NAME_PATTERN = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    private final static String GROUP_ATTRIBUTE = "group";
    private final static String GROUP_NAME_ATTRIBUTE = "groupName";

    /**
     * The associated configuration to this Policy
     */
    private DynamicRoutingPolicyConfiguration configuration;

    /**
     * Create a new policy instance based on its associated configuration
     *
     * @param configuration the associated configuration to the new policy instance
     */
    public DynamicRoutingPolicy(DynamicRoutingPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        try {
            String path = URLDecoder.decode(request.path(), Charset.defaultCharset().name());
            String contextPath = (String) executionContext.getAttribute(ExecutionContext.ATTR_CONTEXT_PATH);
            String subPath = path.substring(contextPath.length());

            LOGGER.debug("Dynamic routing for path {}", subPath);

            if (configuration.getRules() != null && !configuration.getRules().isEmpty()) {
                // Look for a matching pattern from rules
                Optional<Rule> optRule = configuration.getRules().stream().filter(
                        rule -> rule.getPattern().matcher(subPath).matches()).findFirst();

                if (optRule.isPresent()) {
                    Rule rule = optRule.get();

                    LOGGER.debug("Applying rule for path {}: [{} - {}]", subPath, rule.getPattern(), rule.getUrl());
                    String endpoint = rule.getUrl();

                    // Apply regex capture / replacement
                    Matcher match = rule.getPattern().matcher(subPath);

                    // Required to calculate capture groups
                    match.matches();

                    // Extract capture group by index
                    String[] groups = new String[match.groupCount()];
                    for (int idx = 0; idx < match.groupCount(); idx++) {
                        groups[idx] = match.group(idx + 1);
                    }
                    executionContext.getTemplateEngine().getTemplateContext().setVariable(GROUP_ATTRIBUTE, groups);

                    // Extract capture group by name
                    Set<String> extractedGroupNames = getNamedGroupCandidates(rule.getPattern().pattern());
                    Map<String, String> groupNames = extractedGroupNames.stream().collect(
                            Collectors.toMap(groupName -> groupName, match::group));
                    executionContext.getTemplateEngine().getTemplateContext().setVariable(GROUP_NAME_ATTRIBUTE, groupNames);

                    // Given endpoint can be defined as the template using EL
                    LOGGER.debug("Transform endpoint {} using template engine", endpoint);
                    endpoint = executionContext.getTemplateEngine().convert(endpoint);

                    // Set final endpoint
                    executionContext.setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, endpoint);
                    LOGGER.debug("Route request to {}", endpoint);

                    // Add useRawPath parameter in ExecutionContext
                    executionContext.setAttribute(ExecutionContext.ATTR_ENDPOINT_RESOLVER_USE_RAW_PATH, rule.getUseRawPath());
                    LOGGER.debug("useRawPath set to {}", rule.getUseRawPath());
                    
                    // And continue request processing....
                    policyChain.doNext(request, response);
                } else {
                    LOGGER.warn("No defined rule is matching path {}", subPath);
                    // No rule is matching request path
                    policyChain.failWith(PolicyResult.failure(HttpStatusCode.BAD_REQUEST_400, "No routing rule is matching path"));
                }
            } else {
                // No rule defined
                policyChain.doNext(request, response);
            }
        } catch (UnsupportedEncodingException uee) {
            // Invalid path
            policyChain.failWith(PolicyResult.failure(HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Invalid path"));
        }
    }

    private Set<String> getNamedGroupCandidates(String regex) {
        Set<String> namedGroups = new TreeSet<>();
        Matcher m = GROUP_NAME_PATTERN.matcher(regex);

        while (m.find()) {
            namedGroups.add(m.group(1));
        }

        return namedGroups;
    }
}
