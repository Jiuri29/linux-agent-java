<template>
  <div class="nf-wrap">
    <div class="nf-code">404</div>
    <div class="nf-title">页面不存在</div>
    <div class="nf-desc">您访问的页面已被移动、删除或从未存在</div>
    <div class="nf-actions">
      <el-button type="primary" @click="$router.push('/')">返回总览</el-button>
      <el-button @click="$router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.nf-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  padding: 24px;
}
.nf-code {
  font-size: 88px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.04em;
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-light) 60%, var(--brand-soft) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.nf-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.nf-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}
.nf-actions {
  display: flex;
  gap: 10px;
}
</style>
