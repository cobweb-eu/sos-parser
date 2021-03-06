/**
 * ﻿Copyright (C) 2007 - 2014 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */

package org.n52.wps.client.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.InputType;
import net.opengis.wps.x100.ProcessDescriptionType;

import org.junit.Assert;
import org.junit.Test;
import org.n52.wps.client.ExecuteRequestBuilder;
import org.n52.wps.server.algorithm.test.MultiReferenceBinaryInputAlgorithm;

public class ExecuteRequestBuilderTest {

    @Test
    public void addComplexDataInputType() {
        ProcessDescriptionType processDescriptionType = new MultiReferenceBinaryInputAlgorithm().getDescription();

        ExecuteRequestBuilder executeRequestBuilder = new ExecuteRequestBuilder(processDescriptionType);

        InputType inputType = InputType.Factory.newInstance();

        String id = "data";
        inputType.addNewIdentifier().setStringValue(id);
        String url = "http://xyz.test.data";
        inputType.addNewReference().setHref(url);

        executeRequestBuilder.addComplexData(inputType);

        ExecuteDocument request = executeRequestBuilder.getExecute();

        Assert.assertThat("generated doc contains input id", request.toString(), containsString(id));
        Assert.assertThat("generated doc contains input url", request.toString(), containsString(url));
        Assert.assertThat("document is valid", request.validate(), is(true));
    }

}
