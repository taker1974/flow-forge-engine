/*
 * Copyright 2026 Konstantin Terskikh
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

package ru.spb.tksoft.flowforge.engine.model;

import ru.spb.tksoft.flowforge.sdk.contract.RunnableStateChangeListener;
import ru.spb.tksoft.flowforge.sdk.enumeration.RunnableState;
import ru.spb.tksoft.flowforge.sdk.model.BlockBaseImpl;
import ru.spb.tksoft.flowforge.sdk.model.RunnableStateChangedEvent;

/**
 * Block base test implementation.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2026
 */
public final class BlockBaseTest extends BlockBaseImpl
        implements RunnableStateChangeListener {

    public BlockBaseTest(final String internalBlockId,
            final String defaultInputText) {

        super("test-block", internalBlockId, defaultInputText);
        addStateChangeListener(this);
    }

    public static final int COUNT_MAX = 5;
    private int counter = 0;

    @Override
    public void onStateChanged(RunnableStateChangedEvent event) {

        if (event.getNewState() == RunnableState.RUNNING) {
            counter = 0;
        }

        if (event.getNewState() == RunnableState.DONE) {
            goFurtherNormal();
        }
    }

    @Override
    public synchronized void run() {

        super.run();

        if (getState() == RunnableState.RUNNING) {
            counter++;

            if (counter > COUNT_MAX) {
                String resultText = "Result text of the " + getInternalBlockId() + ": " + counter;
                setResultText(resultText);
                setState(RunnableState.DONE);
            }
        }
    }
}
