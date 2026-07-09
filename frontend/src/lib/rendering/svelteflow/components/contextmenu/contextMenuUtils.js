/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

function getRequestPosition(request) {
    return request ? { x: request.x, y: request.y } : { x: 0, y: 0 };
}

export function getContextMenuTriggerStyle(request) {
    const { x, y } = getRequestPosition(request);
    return `left: ${x}px; top: ${y}px;`;
}

export function syncContextMenuTrigger({
    disabled,
    request,
    triggerRef,
    setOpen,
}) {
    if (disabled || !request) {
        setOpen(false);
        return;
    }
    if (!triggerRef) {
        return;
    }

    queueMicrotask(() => {
        triggerRef.dispatchEvent(
            new MouseEvent("contextmenu", {
                bubbles: true,
                cancelable: true,
                button: 2,
                buttons: 2,
                clientX: request.x,
                clientY: request.y,
                view: window,
            }),
        );
    });
}

export function handleContextMenuOpenChange(nextOpen, setOpen, onClose) {
    setOpen(nextOpen);
    if (!nextOpen) {
        onClose();
    }
}
