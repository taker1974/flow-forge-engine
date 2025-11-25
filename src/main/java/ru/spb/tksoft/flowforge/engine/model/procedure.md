<!--
/*
 * Copyright 2025 Konstantin Terskikh
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
-->

# Структура исполняемой процедуры по опыту предыдущих проектов

## Члены

- Атрибуты;
- Список блоков;
- Список линий.

## Методы

- Добавление блоков;
- Добавление линий;
- Соединение блоков линиями;
- Методы runnable.

## Run

Описание без "обёртки" в виде задачи, без многопоточки и т.п. - только основа.

```java
// План выполнения
List<Block> plan = []

// Запускается много раз внешним "тиком",
// оборачивается в вечный цикл внутри задачи и т.п.
instance.Run() {

    if (!RUNNING) {
        
        State = READY ? -> RUNNING
        forEach(block in blocks) {
            if (!block.hasInputs) {
                plan.add(block)
            }
        }
    }

    if (RUNNING) {

        // выполняем блоки из плана
        forEach(block : plan) {
            block.Run()

            // удаляем из плана выполненные
            if (block.state == DONE) {
                plan.remove(block)
            }
        }
        
        // ищем блоки, в которые ведут включенные линии
        // и добавляем их в план (если ещё не добавлены)
        linesOn = lines.filter(LineState.ON)
        forEach(line : linesOn) {
            plan.addIfNotYet(line.blockTo)
        }
    }

    // Завершаем выполнение, если нечего больше выполнять
    if (plan.isEmpty) {
        State = DONE
    }
}
```
