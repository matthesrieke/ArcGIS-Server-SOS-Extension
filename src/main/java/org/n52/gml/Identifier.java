/**
 * Copyright (C) 2012 52°North Initiative for Geospatial Open Source Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.gml;

import java.net.URI;

/**
 * This class represents a GML identifier with a code space and an ID value.
 * 
 * @author Kiesow, staschc
 * @author <a href="mailto:broering@52north.org">Arne Broering</a>
 */
public class Identifier {

    private URI codeSpace;

    private String idValue;

    /**
     * @param codeSpace
     * @param idValue
     */
    public Identifier(URI codeSpace, String idValue) {
        super();
        this.codeSpace = codeSpace;
        this.idValue = idValue;
    }

    public URI getCodeSpace()
    {
        return codeSpace;
    }

    public String getIdentifierValue()
    {
        return idValue;
    }

}
