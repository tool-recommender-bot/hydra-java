/*
 * Copyright (c) 2014. Escalon System-Entwicklung, Dietrich Schulten
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.escalon.hypermedia.spring;

import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.UriTemplate;
import org.springframework.util.Assert;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by dschulten on 01.12.2014.
 */
public class PartialUriTemplate {

    private static final Pattern VARIABLE_REGEX = Pattern.compile("\\{([\\?\\&#/]?)([\\w\\,]+)\\}");

    private final List<String> urlComponents = new ArrayList<String>();

    private final List<List<Integer>> variableIndices = new ArrayList<List<Integer>>();
    private List<TemplateVariable> variables = new ArrayList<TemplateVariable>();
    private List<String> variableNames = new ArrayList<String>();

    /**
     * Creates a new {@link PartialUriTemplate} using the given template string.
     *
     * @param template must not be {@literal null} or empty.
     */
    public PartialUriTemplate(String template) {
        Assert.hasText(template, "Template must not be null or empty!");

        Matcher matcher = VARIABLE_REGEX.matcher(template);

        int endOfPart = 0;
        while (matcher.find()) {

            int startOfPart = matcher.start(0);
            // add part before current match
            if (endOfPart < startOfPart) {
                final String partWithoutVariables = template.substring(endOfPart, startOfPart);
                final StringTokenizer stringTokenizer = new StringTokenizer(partWithoutVariables, "?", true);
                boolean inQuery = false;
                while (stringTokenizer.hasMoreTokens()) {
                    final String token = stringTokenizer.nextToken();
                    if ("?".equals(token)) {
                        inQuery = true;
                    } else {
                        if (!inQuery) {
                            urlComponents.add(token);
                        } else {
                            urlComponents.add("?" + token);
                        }
                        variableIndices.add(Collections.<Integer>emptyList());
                    }

                }

            }
            endOfPart = matcher.end(0);

            // add current match as part
            final String variablePart = template.substring(startOfPart, endOfPart);
            urlComponents.add(variablePart);

            // collect variablesInPart and track for each part which variables it contains
            TemplateVariable.VariableType type = TemplateVariable.VariableType.from(matcher.group(1));
            String[] names = matcher.group(2)
                    .split(",");
            List<Integer> variablesInPart = new ArrayList<Integer>();
            for (String name : names) {
                TemplateVariable variable = new TemplateVariable(name, type);
                variablesInPart.add(variables.size());
                variables.add(variable);
                variableNames.add(name);
            }
            variableIndices.add(variablesInPart);
        }
        // finish off remaining part
        if (endOfPart < template.length()) {
            urlComponents.add(template.substring(endOfPart));
            variableIndices.add(Collections.<Integer>emptyList());
        }

    }

    public List<String> getVariableNames() {
        return variableNames;
    }

    UriTemplateComponents unexpandedComponents() {
        return expand(Collections.<String, Object>emptyMap());
    }

    public UriTemplateComponents expand(Map<String, Object> parameters) {
        Assert.notNull(parameters, "Parameters must not be null!");

        final StringBuilder baseUrl = new StringBuilder(urlComponents.get(0));
        final StringBuilder queryHead = new StringBuilder();
        final StringBuilder queryTail = new StringBuilder();
        final StringBuilder fragmentIdentifier = new StringBuilder();
        for (int i = 1; i < urlComponents.size(); i++) {
            final String part = urlComponents.get(i);
            final List<Integer> variablesInPart = variableIndices.get(i);
            if (variablesInPart.isEmpty()) {
                if (part.startsWith("?") || part.startsWith("&")) {
                    queryHead.append(part);
                } else if (part.startsWith("#")) {
                    fragmentIdentifier.append(part);
                } else {
                    baseUrl.append(part);
                }
            } else {
                for (Integer variableInPart : variablesInPart) {
                    final TemplateVariable variable = variables.get(variableInPart);
                    final Object value = parameters.get(variable.getName());
                    if (value == null) {
                        switch (variable.getType()) {
                            case REQUEST_PARAM:
                            case REQUEST_PARAM_CONTINUED:
                                // query vars without value always go last (query tail)
                                if (queryTail.length() > 0) {
                                    queryTail.append(',');
                                }
                                queryTail.append(variable.getName());
                                break;
                            case FRAGMENT:
                                fragmentIdentifier.append(variable.toString());
                                break;
                            default:
                                baseUrl.append(variable.toString());
                        }
                    } else {
                        switch (variable.getType()) {
                            case REQUEST_PARAM:
                            case REQUEST_PARAM_CONTINUED:
                                if (queryHead.length() == 0) {
                                    queryHead.append('?');
                                } else {
                                    queryHead.append('&');
                                }
                                queryHead.append(variable.getName())
                                        .append('=')
                                        .append(urlEncode(value.toString()));
                                break;
                            case SEGMENT:
                                baseUrl.append('/');
                                // fall through
                            case PATH_VARIABLE:
                                if (queryHead.length() != 0) {
                                    // level 1 variable in query
                                    queryHead.append(urlEncode(value.toString()));
                                } else {
                                    if(baseUrl.length() > 0 && baseUrl.charAt(baseUrl.length() - 1) != '/') {
                                        baseUrl.append('/');
                                    }
                                    baseUrl.append(urlEncode(value.toString()));
                                }
                                break;
                            case FRAGMENT:
                                fragmentIdentifier.append('#');
                                fragmentIdentifier.append(urlEncode(value.toString()));
                                break;
                        }
                    }
                }
            }
        }


        return new UriTemplateComponents(baseUrl.toString(), queryHead.toString(), queryTail.toString(),
                fragmentIdentifier.toString());

    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("failed to urlEncode " + s, e);
        }
    }
}
