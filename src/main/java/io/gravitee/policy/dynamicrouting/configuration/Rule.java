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
package io.gravitee.policy.dynamicrouting.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.gravitee.policy.dynamicrouting.configuration.jackson.PatternDeserializer;

import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class Rule {

    @JsonDeserialize(using = PatternDeserializer.class)
    private Pattern pattern;

    private String url;
    
    private Boolean useRawPath;

    public Rule() {
    }

    public Rule(Pattern pattern, String url) {
        this(pattern, url, Boolean.FALSE);
    }
    
    public Rule(Pattern pattern, String url, Boolean useRawPath) {
        this.pattern = pattern;
        this.url = url;
        this.useRawPath = useRawPath;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

	public Boolean getUseRawPath() {
		return useRawPath;
	}

	public void setUseRawPath(Boolean useRawPath) {
		this.useRawPath = useRawPath;
	}
    
    
}
