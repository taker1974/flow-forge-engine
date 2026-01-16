/*
 * Copyright 2025 Konstantin Terskikh
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package ru.spb.tksoft.flowforge.engine.contract;

import java.util.List;
import ru.spb.tksoft.flowforge.sdk.contract.Runnable;
import ru.spb.tksoft.flowforge.sdk.contract.Modifiable;
import ru.spb.tksoft.flowforge.sdk.contract.EventProducer;
import ru.spb.tksoft.flowforge.engine.model.ModifiableChangedEvent;
import ru.spb.tksoft.flowforge.sdk.contract.Pausable;

/**
 * Instance interface.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public interface Instance
        extends Runnable, Pausable,
        Modifiable, EventProducer<ModifiableChangedEvent> {

    /**
     * Get the source template id.
     * 
     * @return the source template id.
     */
    Long getTemplateId();

    /**
     * Get the instance id.
     * 
     * @return the instance id.
     */
    Long getInstanceId();

    /**
     * Get the instance user id.
     * 
     * Instance user id is the id of the user who created the instance.
     * 
     * @return the instance user id.
     */
    Long getInstanceUserId();

    /**
     * Get the instance name.
     * 
     * Instance name is based on the source template name or can be set by the user.
     * 
     * @return the instance name.
     */
    String getInstanceName();

    /**
     * Get the modified objects.
     * 
     * @return the modified objects.
     */
    List<Modifiable> getModifiedObjects();
}
