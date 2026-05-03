<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NSpin } from 'naive-ui';
import { request } from '@/service/request';
import { getFileExt } from '@/utils/common';
import SvgIcon from '@/components/custom/svg-icon.vue';

interface Props {
  fileName: string;
  visible: boolean;
}

interface Emits {
  (e: 'close'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const loading = ref(false);
const downloading = ref(false);
const content = ref('');
const error = ref('');
const imageUrl = ref('');

// 获取文件扩展名
const fileExt = computed(() => {
  return getFileExt(props.fileName).toLowerCase();
});

// 判断文件类型
const isTextFile = computed(() => {
  const textExts = [
    'txt',
    'md',
    'json',
    'xml',
    'html',
    'htm',
    'css',
    'js',
    'ts',
    'py',
    'java',
    'c',
    'cpp',
    'h',
    'hpp',
    'php',
    'rb',
    'go',
    'rs',
    'sh',
    'bat',
    'cmd',
    'csv',
    'log',
    'yaml',
    'yml',
    'properties',
    'conf',
    'config'
  ];
  return textExts.includes(fileExt.value);
});

const isImageFile = computed(() => {
  const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'];
  return imageExts.includes(fileExt.value);
});

const isPdfFile = computed(() => {
  return fileExt.value === 'pdf';
});

const isOfficeFile = computed(() => {
  const officeExts = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'odp'];
  return officeExts.includes(fileExt.value);
});

// 获取文件图标
function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

// 监听文件名变化，加载预览内容
watch(
  () => props.fileName,
  async newFileName => {
    if (newFileName && props.visible) {
      await loadPreviewContent();
    }
  },
  { immediate: true }
);

// 监听可见性变化
watch(
  () => props.visible,
  async visible => {
    if (visible && props.fileName) {
      await loadPreviewContent();
    }
  }
);

// 加载预览内容
async function loadPreviewContent() {
  if (!props.fileName) return;

  loading.value = true;
  error.value = '';
  content.value = '';
  imageUrl.value = '';

  try {
    // 只对文本文件加载预览内容
    if (isTextFile.value) {
      const { error: requestError, data } = await request<{
        fileName: string;
        content: string;
        fileSize: number;
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName
        }
      });

      if (requestError) {
        error.value = `预览失败：${requestError.message || '未知错误'}`;
      } else if (data) {
        content.value = data.content || '无预览内容';
      }
    }
    // 对图片文件处理
    else if (isImageFile.value) {
      // 图片文件可以通过直接访问URL来预览
      // 这里可以根据实际情况生成图片URL
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download',
        params: {
          fileName: props.fileName
        }
      });

      if (requestError) {
        error.value = `预览失败：${requestError.message || '未知错误'}`;
      } else if (data) {
        imageUrl.value = data.downloadUrl;
      }
    }
    // 其他文件类型不需要加载内容
  } catch (err: any) {
    error.value = `预览失败：${err.message || '网络错误'}`;
  } finally {
    loading.value = false;
  }
}

// 下载文件
async function downloadFile() {
  if (!props.fileName) return;

  downloading.value = true;

  try {
    const { error: requestError, data } = await request<{
      fileName: string;
      downloadUrl: string;
      fileSize: number;
    }>({
      url: '/documents/download',
      params: {
        fileName: props.fileName
      }
    });

    if (requestError) {
      window.$message?.error(`下载失败：${requestError.message || '未知错误'}`);
    } else if (data) {
      // 使用预签名URL下载文件
      const link = document.createElement('a');
      link.href = data.downloadUrl;
      link.download = data.fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.$message?.success('开始下载文件');
    }
  } catch (err: any) {
    window.$message?.error(`下载失败：${err.message || '网络错误'}`);
  } finally {
    downloading.value = false;
  }
}

// 关闭预览
function closePreview() {
  emit('close');
}
</script>

<template>
  <div class="file-preview-container">
    <!-- 预览头部 -->
    <div class="preview-header">
      <div class="flex items-center gap-2">
        <SvgIcon :local-icon="getFileIcon(fileName)" class="text-16" />
        <span class="font-medium">{{ fileName }}</span>
      </div>
      <div class="flex items-center gap-2">
        <NButton size="small" :loading="downloading" @click="downloadFile">
          <template #icon>
            <icon-mdi-download />
          </template>
          下载
        </NButton>
        <NButton size="small" @click="closePreview">
          <template #icon>
            <icon-mdi-close />
          </template>
        </NButton>
      </div>
    </div>

    <!-- 预览内容 -->
    <div class="preview-content">
      <template v-if="loading">
        <div class="h-full flex items-center justify-center">
          <NSpin size="large" />
        </div>
      </template>
      <template v-else-if="error">
        <div class="h-full flex flex-col items-center justify-center text-gray-500">
          <icon-mdi-alert-circle class="mb-4 text-48" />
          <p>{{ error }}</p>
        </div>
      </template>
      <template v-else>
        <div class="content-wrapper">
          <!-- 根据文件类型显示不同的预览内容 -->
          <template v-if="isTextFile">
            <!-- 文本文件直接显示 -->
            <pre class="preview-text">{{ content || '无预览内容' }}</pre>
          </template>
          <template v-else-if="isImageFile">
            <!-- 图片文件显示图片 -->
            <div class="image-preview-container">
              <img :src="imageUrl" alt="文件预览" class="image-preview" />
            </div>
          </template>
          <template v-else-if="isPdfFile">
            <!-- PDF文件显示PDF预览 -->
            <div class="pdf-preview-container">
              <div class="pdf-placeholder">
                <icon-mdi-file-pdf-box class="mb-4 text-64 text-red-500" />
                <h3 class="mb-2 text-lg font-medium">{{ fileName }}</h3>
                <p class="mb-4 text-gray-500">PDF文件预览</p>
                <NButton type="primary" @click="downloadFile">
                  <template #icon>
                    <icon-mdi-download />
                  </template>
                  下载查看
                </NButton>
              </div>
            </div>
          </template>
          <template v-else-if="isOfficeFile">
            <!-- Office文件显示提示 -->
            <div class="office-preview-container">
              <div class="office-placeholder">
                <SvgIcon :local-icon="getFileIcon(fileName)" class="mb-4 text-64" />
                <h3 class="mb-2 text-lg font-medium">{{ fileName }}</h3>
                <p class="mb-4 text-gray-500">Office文件预览</p>
                <NButton type="primary" @click="downloadFile">
                  <template #icon>
                    <icon-mdi-download />
                  </template>
                  下载查看
                </NButton>
              </div>
            </div>
          </template>
          <template v-else>
            <!-- 其他文件类型显示提示 -->
            <div class="other-preview-container">
              <div class="other-placeholder">
                <icon-mdi-file-outline class="mb-4 text-64 text-gray-400" />
                <h3 class="mb-2 text-lg font-medium">{{ fileName }}</h3>
                <p class="mb-4 text-gray-500">不支持的文件预览类型</p>
                <NButton type="primary" @click="downloadFile">
                  <template #icon>
                    <icon-mdi-download />
                  </template>
                  下载查看
                </NButton>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.file-preview-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: white;
  border-left: 1px solid #e5e7eb;

  .preview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem;
    border-bottom: 1px solid #e5e7eb;
    background-color: #f9fafb;
  }

  .preview-content {
    flex: 1;
    overflow: hidden;

    .content-wrapper {
      height: 100%;
      overflow: auto;
      padding: 1rem;
    }

    /* 文本文件预览样式 */
    .preview-text {
      font-size: 0.875rem;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      white-space: pre-wrap;
      word-break: break-all;
      line-height: 1.5;
      margin: 0;
      color: #374151;
      background-color: white;
    }

    /* 图片文件预览样式 */
    .image-preview-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100%;
      background-color: #f9fafb;
      border-radius: 0.5rem;

      .image-preview {
        max-width: 100%;
        max-height: 100%;
        object-fit: contain;
        border-radius: 0.25rem;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      }
    }

    /* PDF文件预览样式 */
    .pdf-preview-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100%;
      background-color: #f9fafb;
      border-radius: 0.5rem;

      .pdf-placeholder {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 2rem;
      }
    }

    /* Office文件预览样式 */
    .office-preview-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100%;
      background-color: #f9fafb;
      border-radius: 0.5rem;

      .office-placeholder {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 2rem;
      }
    }

    /* 其他文件类型预览样式 */
    .other-preview-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100%;
      background-color: #f9fafb;
      border-radius: 0.5rem;

      .other-placeholder {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 2rem;
      }
    }
  }
}
</style>
