<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import LinkCreateForm from "../components/links/LinkCreateForm.vue";
import LinkListTable from "../components/links/LinkListTable.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { useLinksPage } from "../composables/useLinksPage";

const navigation = useAppSessionNavigation("links");
const page = useLinksPage();
</script>

<template>
  <AppPageShell
    :title="navigation.title"
    :user-email="navigation.userEmail.value"
    :nav-items="navigation.navItems"
    @navigate="navigation.navigate"
    @logout="navigation.logout"
  >
    <LinkCreateForm
      :form="page.createForm"
      :creating="page.creating.value"
      :importing="page.importing.value"
      :import-file-name="page.importFileName.value"
      :is-admin="navigation.isAdmin.value"
      :error="page.error.value"
      @create="page.createLink"
      @import="page.importCsv"
      @export="page.exportCsv"
      @file-change="page.setImportFile"
    />

    <LinkListTable
      :items="page.items.value"
      :loading="page.loading.value"
      :error="page.error.value"
      :show-archived="page.filters.showArchived"
      :keyword="page.filters.keyword"
      :editing-id="page.editingId.value"
      :edit-form="page.editForm"
      :is-admin="navigation.isAdmin.value"
      :format-instant-local="page.formatInstantLocal"
      :policy-summary="page.policySummary"
      :status-label="page.statusLabel"
      @refresh="page.load"
      @set-archived="page.setArchived"
      @update:keyword="page.filters.keyword = $event"
      @start-edit="page.startEdit"
      @cancel-edit="page.cancelEdit"
      @save-edit="page.saveEdit"
      @toggle-enabled="page.toggleEnabled"
      @archive="page.archiveLink"
      @restore="page.restoreLink"
      @delete="page.deleteLink"
    />
  </AppPageShell>
</template>
