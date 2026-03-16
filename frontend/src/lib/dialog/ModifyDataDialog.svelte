<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->
<script>
    import { faRotateLeft, faSave } from "@fortawesome/free-solid-svg-icons";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";

    let {
        showDialog = $bindable(),
        onOpen = () => {},
        onClose = () => {},
        size,
        saveChanges = () => {},
        discardChanges = () => {},
        hasChanges = false,
        isValid = true,
        readonly,
        title,
        children,
    } = $props();

    let showDiscardSaveConfirmDialog = $state(false);

    function closeDialog(triggerConfirmDialog) {
        if (triggerConfirmDialog && hasChanges) {
            showDiscardSaveConfirmDialog = true;
            return false;
        }
        if (hasChanges) {
            discardChanges();
        }
        showDialog = false;
        onClose();
        return true;
    }

    ///////// confirm dialog  /////////

    function discard() {
        showDialog = false;
        discardChanges();
    }

    function save() {
        showDialog = false;
        saveChanges();
    }
</script>

//TODO: dialog closes before confirmd ialog opens and closen on primary
<ActionDialog
    bind:showDialog
    {onOpen}
    onClose={() => closeDialog(true)}
    {size}
    secondaryLabel={"Discard"}
    secondaryIcon={faRotateLeft}
    secondaryVariant={"danger"}
    onSecondary={() => closeDialog(false)}
    primaryLabel={hasChanges ? "Save" : "No Changes"}
    onPrimary={save}
    closeOnPrimary={false}
    disablePrimary={!hasChanges || !isValid}
    primaryIcon={faSave}
    {readonly}
    {children}
    {title}
/>
<DiscardCancelConfirmDialog
    bind:showDialog={showDiscardSaveConfirmDialog}
    onDiscard={discard}
    onSave={save}
    disableSave={!hasChanges || !isValid}
/>
