import { fakePaginationRequest, request } from '../request';

export function fetchGetOrgTagList() {
  return fakePaginationRequest<Api.OrgTag.List>({ url: '/admin/org-tags' });
}

export function fetchGetOrgTagTree() {
  return request<Api.OrgTag.List>({ url: '/admin/org-tags/tree' });
}
