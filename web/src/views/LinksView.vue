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
    <section v-if="navigation.isAdmin.value" class="scope card">
      <h2>应用范围</h2>
      <div class="scope-grid">
        <label class="field">
          <span class="sub">应用</span>
          <select
            :value="page.selectedApplicationId.value ?? ''"
            @change="page.setSelectedApplicationId(($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"
          >
            <option value="">全部应用</option>
            <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
              {{ application.displayName }}
            </option>
          </select>
        </label>
        <label v-if="page.selectedApplicationId.value" class="field">
          <span class="sub">创建域名</span>
          <select
            :value="page.selectedDomainId.value ?? ''"
            @change="page.setSelectedDomainId(($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"
          >
            <option value="">选择域名</option>
            <option v-for="domain in page.availableDomains.value" :key="domain.id" :value="domain.id">
              {{ domain.hostname }} / {{ domain.scope }}
            </option>
          </select>
        </label>
      </div>
    </section>

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
      :page="page.page.value"
      :size="page.size.value"
      :total="page.total.value"
      :format-instant-local="page.formatInstantLocal"
      :policy-summary="page.policySummary"
      :status-label="page.statusLabel"
      @refresh="page.load"
      @set-archived="page.setArchived"
      @update:keyword="page.setKeyword"
      @previous-page="page.previousPage"
      @next-page="page.nextPage"
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

<style scoped>
.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sub {
  color: #666;
  font-size: 12px;
}

select {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}
</style>
