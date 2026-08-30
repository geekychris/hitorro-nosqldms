// Thin fetch wrapper — one function per REST endpoint the UI needs.

export interface ContentRef {
  role: string;
  mime: string;
  sizeBytes: number;
  sha256: string;
  url?: string;
  generatedBy?: string;
  derivedFromRole?: string;
  sourceVersionId?: string;
  attachedAt?: string;
}

export interface Document {
  versionId: string;
  canonicalId: string;
  versionLabel: string;
  versionMajor: number;
  versionMinor: number;
  versionPatch: number;
  versionQualifier?: string;
  versionQualNumber?: number;
  versionBuild: number;
  versionKind: string;
  parentVersion?: string;
  branchOf?: string;
  isHead: boolean;
  isStable: boolean;
  title?: string;
  body?: string;
  description?: string;
  contentType?: string;
  createdBy?: string;
  modifiedBy?: string;
  createdAt?: string;
  modifiedAt?: string;
  contentRefs: ContentRef[];
  tombstoned?: boolean;
}

export interface Reference {
  fromCanonical: string;
  toCanonical: string;
  toVersion?: string;
  kind: string;
  aux?: string;
  createdAt?: string;
}

export interface Grant {
  canonicalId: string;
  principal: string;
  permission: string;
  grant: boolean;
  inheritFrom?: string;
  grantedAt?: string;
}

export interface FolderMembership {
  folderCanonical: string;
  childCanonical: string;
  addedBy?: string;
  addedAt?: string;
}

export interface SearchHit {
  versionId: string;
  canonicalId: string;
  versionLabel: string;
  title?: string;
  score: number;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function json(res: Response): Promise<any> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json();
}

export const dms = {
  listDocuments: (): Promise<string[]> =>
    fetch('/api/documents').then(json),

  getHead: (id: string): Promise<Document> =>
    fetch(`/api/documents/${id}`).then(json),

  listVersions: (id: string): Promise<Document[]> =>
    fetch(`/api/documents/${id}/versions`).then(json),

  create: (body: Partial<Document> & { createdBy?: string }): Promise<Document> =>
    fetch('/api/documents', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    }).then(json),

  checkIn: (id: string, req: {
    title?: string; body?: string; description?: string;
    modifiedBy?: string; bumpKind?: 'MAJOR' | 'MINOR' | 'PATCH' | 'QUALIFIER';
    qualifier?: string;
  }): Promise<Document> =>
    fetch(`/api/documents/${id}/versions`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(req),
    }).then(json),

  tombstone: (id: string): Promise<void> =>
    fetch(`/api/documents/${id}`, { method: 'DELETE' }).then(() => undefined),

  renditions: (id: string): Promise<ContentRef[]> =>
    fetch(`/api/documents/${id}/renditions`).then(json),

  putRendition: (id: string, versionId: string, role: string, mime: string, blob: Blob): Promise<Document> =>
    fetch(`/api/documents/${id}/versions/${versionId}/renditions/${role}`, {
      method: 'PUT',
      headers: { 'content-type': mime },
      body: blob,
    }).then(json),

  deleteRendition: (id: string, versionId: string, role: string): Promise<void> =>
    fetch(`/api/documents/${id}/versions/${versionId}/renditions/${role}`, { method: 'DELETE' }).then(() => undefined),

  renditionUrl: (id: string, role: string) => `/api/documents/${id}/renditions/${role}`,

  references: (id: string): Promise<Reference[]> =>
    fetch(`/api/documents/${id}/references`).then(json),

  inbound: (id: string): Promise<Reference[]> =>
    fetch(`/api/documents/${id}/references/inbound`).then(json),

  addReference: (id: string, ref: Partial<Reference>): Promise<Reference> =>
    fetch(`/api/documents/${id}/references`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(ref),
    }).then(json),

  acls: (id: string): Promise<Grant[]> =>
    fetch(`/api/documents/${id}/acls`).then(json),

  grant: (id: string, g: Partial<Grant>): Promise<Grant> =>
    fetch(`/api/documents/${id}/acls`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(g),
    }).then(json),

  revoke: (id: string, principal: string, permission: string): Promise<void> =>
    fetch(`/api/documents/${id}/acls/${encodeURIComponent(principal)}/${permission}`, { method: 'DELETE' })
      .then(() => undefined),

  folderContents: (folder: string): Promise<FolderMembership[]> =>
    fetch(`/api/folders/${folder}/contents`).then(json),

  foldersForDoc: (id: string): Promise<FolderMembership[]> =>
    fetch(`/api/folders/for-doc/${id}`).then(json),

  linkFolder: (folder: string, child: string, addedBy = 'ui'): Promise<void> =>
    fetch(`/api/folders/${folder}/contents`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ child, addedBy }),
    }).then(() => undefined),

  unlinkFolder: (folder: string, child: string): Promise<void> =>
    fetch(`/api/folders/${folder}/contents/${child}`, { method: 'DELETE' })
      .then(() => undefined),

  search: (q: string, limit = 20): Promise<SearchHit[]> =>
    fetch(`/api/search?q=${encodeURIComponent(q)}&limit=${limit}`).then(json),
};
