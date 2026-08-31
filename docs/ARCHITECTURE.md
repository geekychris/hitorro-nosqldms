# hitorro-nosqldms — Architecture, Build, & API Reference

Distributed, NoSQL-backed document management system on JVS types +
KV storage + Lucene. This document covers the architecture, module
layout, storage model, build process, Java API, and full REST API.

- [1. Overview](#1-overview)
- [2. Module layout](#2-module-layout)
- [3. Runtime architecture](#3-runtime-architecture)
- [4. Data model](#4-data-model)
- [5. Storage layer](#5-storage-layer)
- [6. Type system](#6-type-system)
- [7. Copy-on-write rendition model](#7-copy-on-write-rendition-model)
- [8. Build & run](#8-build--run)
- [9. Configuration](#9-configuration)
- [10. Java API](#10-java-api)
- [11. REST API](#11-rest-api)
- [12. Extending](#12-extending)
- [13. Design notes + tradeoffs](#13-design-notes--tradeoffs)

---

## 1. Overview

### What it is

An HTTP + web-UI document management service that stores every doc as
a versioned JVS-typed record with content-addressed binary renditions
— no RDBMS anywhere. Content lives in a KV keyspace, metadata lives
in sibling KV keyspaces, search runs off Lucene.

### Design principles

1. **NoSQL only.** Every persistent value is either a KV entry (JSON
   or bytes, keyed by a composite string) or a Lucene index entry
   (derived, rebuildable). No SQL, no schema migrations, no ORM.
2. **Framework-neutral core.** All types, storage abstractions, the
   `DocumentService`, and the Lucene index live in
   `hitorro-nosqldms-core` — pure Java, only Jackson + Lucene as
   compile-time deps. Spring is a runtime wrapper, not a requirement.
3. **Copy-on-write per rendition.** A version's `content_refs`
   manifest is shallow-copied on check-in; only rendition entries
   that are actually replaced split off new blobs. Metadata-only
   check-in of a 500 MB doc copies zero bytes.
4. **Minimal-update principle.** References, folder memberships,
   ACL grants, and tags each live in their own KV keyspace — never
   on the document body. Adding a citation, granting an ACL, or
   linking to a folder never rewrites the document.
5. **JVS-typed documents.** Every doc has a `typeName` mapping to a
   `TypeDef` (JVS-inspired). Field definitions drive the UI's
   dynamic form + expose per-field query surface in Lucene.
6. **Content-addressed blobs.** Bytes stored once per unique sha256.
   Identical bytes across two versions dedup to one stored blob.

## 2. Module layout

```
hitorro-nosqldms/
├── pom.xml                                  ← parent (aggregator)
├── hitorro-nosqldms-core/                   ← framework-neutral core
│   ├── src/main/java/com/hitorro/dms/
│   │   ├── model/       Document, VersionLabel, ContentRef, Reference, Grant, FolderMembership, Blob, TypeDef
│   │   ├── store/       DocumentStore, ReferenceStore, FolderStore, AclStore, TagStore  (interfaces)
│   │   ├── store/mem/   in-memory impls of all five
│   │   ├── blob/        BlobStore + InMemoryBlobStore
│   │   ├── index/       IndexWriter + IndexSearcher interfaces
│   │   ├── index/lucene/ LuceneIndex (implements both)
│   │   ├── service/     DocumentService, TypeRegistry, CreateRequest, CheckInRequest
│   │   └── context/     DmsContext (framework-neutral service registry)
│   └── src/main/resources/dms-types/  ← bundled JVS type JSONs
├── hitorro-nosqldms-spring-boot/            ← Spring Boot 3 runtime
│   ├── src/main/java/com/hitorro/dms/spring/
│   │   ├── DmsApplication          @SpringBootApplication entry point
│   │   ├── config/DmsAutoConfiguration + DmsProperties
│   │   └── web/                    DocumentsController, RenditionsController, FoldersController,
│   │                               ReferencesController, AclsController, TagsController,
│   │                               SearchController, TypesController
│   ├── src/main/resources/application.yml
│   └── src/main/resources/static/  ← built React UI (populated by pnpm build)
└── hitorro-nosqldms-web/                    ← React 18 + Vite + TypeScript UI
    ├── src/
    │   ├── App.tsx           App shell + router
    │   ├── api/dms.ts        typed fetch wrapper (one function per REST endpoint)
    │   ├── components/       TypedFieldsForm (renders a TypeDef as a form)
    │   └── pages/            Documents / DocumentDetail / Folder / Search
    └── vite.config.ts        outputs to ../hitorro-nosqldms-spring-boot/…/static/
```

**Dependency direction** (arrows = "depends on"):

```
hitorro-nosqldms-spring-boot ──► hitorro-nosqldms-core
hitorro-nosqldms-web         ──► (no Java deps; built into spring-boot's static/)
```

The core is deliberately **the only thing you MUST use**. Spring Boot
is a convenience wrapper. The UI is optional (headless deployments
can skip it and hit REST directly).

## 3. Runtime architecture

### Three-tier structure

```
┌──────────────────────────────────────┐
│   Web UI (React 18)                  │  Served from static/ inside the Spring Boot jar
│   - Docs list w/ type filter         │  Talks to REST at /api/*
│   - Document detail w/ dynamic form  │
│   - Folder browser + Search          │
└──────────────────────────────────────┘
                  │  HTTP (JSON + binary)
                  ▼
┌──────────────────────────────────────┐
│   REST controllers (Spring MVC)      │  hitorro-nosqldms-spring-boot
│   - DocumentsController              │
│   - RenditionsController             │
│   - FoldersController                │
│   - ReferencesController             │
│   - AclsController                   │
│   - TagsController                   │
│   - TypesController                  │
│   - SearchController                 │
└──────────────────────────────────────┘
                  │  Java method calls
                  ▼
┌──────────────────────────────────────┐
│   Service layer                      │  hitorro-nosqldms-core
│   - DocumentService (orchestrator)   │  Framework-neutral. Also usable
│   - TypeRegistry                     │  standalone via DmsContext.
│   - DmsContext (service registry)    │
└──────────────────────────────────────┘
                  │
        ┌─────────┼──────────┐
        ▼         ▼          ▼
   ┌─────────┐ ┌────────┐ ┌────────────┐
   │ KV      │ │ Blobs  │ │ Lucene idx │
   │ stores  │ │ (sha256│ │ (derived)  │
   │ (5)     │ │  addr) │ │            │
   └─────────┘ └────────┘ └────────────┘
```

### Request lifecycle — create a doc

```
POST /api/documents
  → DocumentsController.create(req)                (REST)
  → DocumentService.create(req)                    (core service)
      ├─ mint canonicalId + versionId
      ├─ build Document POJO (title, body, typeName, typeFields, …)
      ├─ for each rendition in req:
      │    BlobStore.put(bytes) → sha256           (content-addressed)
      │    append ContentRef(role, sha256, …)      to Document
      ├─ DocumentStore.putVersion(doc)             (v|canonical|label KV entry)
      ├─ DocumentStore.setHead(canonicalId, verId) (d|canonical KV entry)
      └─ IndexWriter.indexDocument(doc)            (Lucene: title, body, typed fields, _all catchall)
  ← ResponseEntity<Document>
```

### Request lifecycle — search

```
GET /api/search?q=chris
  → SearchController.search(q, limit)
  → IndexSearcher.search(q, limit)
      ├─ QueryParser (default field = _all)
      ├─ Lucene TopDocs
      └─ map to SearchHit records
  ← List<SearchHit>
```

### Request lifecycle — check-in new version

```
POST /api/documents/{id}/versions
  → DocumentsController.checkIn(id, req)
  → DocumentService.checkIn(req)
      ├─ load previous head from DocumentStore
      ├─ compute next VersionLabel via bump(kind, qualifier)
      ├─ build new Document; content_refs = shallowCopyManifest(head.content_refs)
      ├─ for each replacement rendition in req:
      │    BlobStore.put(bytes)  (dedups if identical bytes exist)
      │    replaceOrAppendRendition on the manifest
      ├─ merge typeFields: head's ∪ request's (request wins per key)
      ├─ mark new as head; demote old
      ├─ DocumentStore.putVersion(nu) + setHead(canonical, nu.versionId)
      ├─ DocumentStore.putVersion(head) (rewrite w/ is_head=false)
      └─ IndexWriter.indexDocument(nu) + indexDocument(head)
```

## 4. Data model

### Document

The primary POJO. See
[`Document.java`](../hitorro-nosqldms-core/src/main/java/com/hitorro/dms/model/Document.java).

| Field | Type | Purpose |
|---|---|---|
| `versionId` | String | Unique per version (`ver-<uuid>`). |
| `canonicalId` | String | Stable across every version (`doc-<uuid>`). |
| `versionLabel` | String | Assembled `MAJOR.MINOR.PATCH[-QUAL[N]][+BUILD]`. |
| `versionMajor` / `versionMinor` / `versionPatch` | long | Numeric parts, individually queryable. |
| `versionQualifier` | String | Pre-release tag; null = stable. |
| `versionQualNumber` | Long | Numeric suffix on the qualifier (`alpha3` → 3). |
| `versionBuild` | long | Monotonic build number across the whole `canonicalId` lineage. `MAX(build)` always identifies newest. |
| `versionKind` | String | `release` / `draft` / `branch` / `hotfix`. |
| `parentVersion` | String | Direct predecessor `versionId`. |
| `isHead` | boolean | Denormalised: true iff current head. |
| `isStable` | boolean | Denormalised: `versionQualifier == null`. |
| `title`, `body`, `description` | String | Content-defining metadata. |
| `contentType` | String | `wiki-page` / `task` / `contact` / `folder` / … |
| `typeName` | String | Registered `TypeDef.name`. Defaults to `contentType`. |
| `typeFields` | `Map<String,Object>` | Type-specific field values (opaque, round-tripped verbatim). |
| `contentRefs` | `List<ContentRef>` | Rendition manifest (copy-on-write). |
| `tombstoned` | boolean | Soft-delete marker. |
| `createdBy`, `modifiedBy`, `createdAt`, `modifiedAt` | | Audit. |

### ContentRef (a rendition entry)

| Field | Type | Purpose |
|---|---|---|
| `role` | String | Rendition name (`primary`, `thumbnail`, `text-extract`, …). |
| `mime` | String | MIME type. |
| `sizeBytes` | long | |
| `sha256` | String | Content address. Same bytes ⇒ same hash ⇒ shared. |
| `url` | String | `blob://{sha256}` / `minio://…` / `s3://…` / `file://…`. |
| `inline` | String | Base64 body for very small blobs (≤ ~4 KB). |
| `generatedBy` | String | `user` (source of truth) or `pipeline:<name>` (derived). |
| `derivedFromRole` | String | For derived: which role it was computed from. |
| `sourceVersionId` | String | Version that first attached this specific entry. Unchanged across shared-content versions; updated only on replace. |
| `attachedAt` | Instant | |

### Reference, Grant, FolderMembership, Blob

Each has its own POJO — see the [model package](../hitorro-nosqldms-core/src/main/java/com/hitorro/dms/model/).
Not stored on the `Document`; each lives in its own store.

## 5. Storage layer

### The six storage interfaces

| Interface | Purpose | Impl provided |
|---|---|---|
| `DocumentStore` | Version + head persistence | `InMemoryDocumentStore` |
| `ReferenceStore` | Inter-doc references (outbound + inbound) | `InMemoryReferenceStore` |
| `FolderStore` | Many-to-many folder ↔ doc links | `InMemoryFolderStore` |
| `AclStore` | Per-(doc × principal) grants | `InMemoryAclStore` |
| `TagStore` | Free-form user tags | `InMemoryTagStore` |
| `BlobStore` | Content-addressed bytes | `InMemoryBlobStore` |

The in-memory impls default in phase 1. Every interface is designed
so a RocksDB-backed impl is a drop-in replacement — see
[Extending](#12-extending).

### KV key encoding (design target for a persistent impl)

The KV layout that the in-memory impls simulate:

| Key pattern | Value | Purpose |
|---|---|---|
| `d\|{canonical}` | version id | Head pointer |
| `v\|{canonical}\|{label}` | JVS doc bytes | The version |
| `l\|{canonical}\|{versionId}` | label | Reverse lookup |
| `r\|{from}\|{kind}\|{to}` | reference metadata | Outbound refs |
| `br\|{to}\|{kind}\|{from}` | reference metadata | Back-refs |
| `f\|{folder}\|{child}` | membership | Folder → child |
| `df\|{child}\|{folder}` | membership | Child → folder |
| `a\|{canonical}\|{principal}` | grant | ACL entry |
| `g\|{principal}\|{canonical}` | grant | Reverse ACL |
| `t\|{canonical}` | tombstone marker | Soft delete |
| `b\|{sha256}` | blob metadata | Content-addressed blob header |
| `blob-body\|{sha256}` | raw bytes | Small/medium blob body |
| `tag\|{canonical}\|{tag}` | tag record | Tags |

### Lucene index

`LuceneIndex` implements both `IndexWriter` and `IndexSearcher`.
Fields written per version:

- **Identity**: `version_id`, `canonical_id`
- **Version**: `version_label` (StringField), `version_major`/`minor`/`patch`/`build` (LongPoint), `version_qualifier`, `version_kind`, `is_head`, `is_stable`
- **Metadata**: `title` (TextField), `body`, `description`, `content_type`, `type_name`, `created_by`, `modified_by`
- **Renditions** (multi-valued facets): `content_refs.role`, `content_refs.mime`
- **Typed fields**: `tf.<fieldName>` per entry in `typeFields`
- **Catchall**: `_all` (title + description + body + typeName + all typed values concatenated)
- **Source**: `_source` StoredField with the full JSON — reconstruct without a KV round-trip

Query parser defaults to `_all`, so bare queries hit anywhere in the
document. Explicit field queries (`title:chris`, `tf.status:open`,
`version_major:2`) still work.

Idempotent updates via `updateDocument(new Term("version_id", …))`.

## 6. Type system

Every document has a `typeName` mapping to a `TypeDef` registered in
the `TypeRegistry`.

### TypeDef

```json
{
  "name":  "task",
  "title": "Task",
  "description": "A single unit of work with an owner, status, and priority.",
  "fields": [
    { "name":"assignee", "kind":"string", "label":"Assignee", "required":true },
    { "name":"status",   "kind":"enum",   "label":"Status",
      "choices":["todo","in-progress","blocked","done"], "required":true },
    { "name":"priority", "kind":"enum",   "label":"Priority",
      "choices":["low","medium","high","urgent"] },
    { "name":"due_date", "kind":"date",   "label":"Due date" },
    { "name":"estimate_h","kind":"double","label":"Estimate (hours)" },
    { "name":"labels",   "kind":"array<string>", "label":"Labels" }
  ]
}
```

### Field kinds

| Kind | UI widget | Storage |
|---|---|---|
| `string` | Text input | String |
| `text` | Textarea (multi-line) | String |
| `long` | Number input (integer) | Long |
| `double` | Number input | Double |
| `boolean` | Checkbox | Boolean |
| `date` | Date input | ISO date string |
| `url` | URL input | String |
| `enum` | Dropdown (from `choices`) | String |
| `array<string>` | Comma-separated text input | List<String> |

Unknown kinds fall back to a plain text input. The UI's
`TypedFieldsForm` component enumerates the widget per kind — extend
it to add more.

### Registry loading

`TypeRegistry` reads bundled types from `classpath:dms-types/*.json`
at startup. An optional user overlay dir (`dms.types-dir` property,
defaults to `${dms.home}/types`) is loaded on top — same-named files
override bundled ones.

Bundled types out of the box: `wiki-page`, `task`, `contact`,
`folder`.

### Query surface

Typed field values are indexed as `tf.<name>` — Lucene classic query
parser understands e.g. `tf.status:in-progress AND tf.priority:high`.
The `_all` catchall also includes typed values so bare queries hit
them.

## 7. Copy-on-write rendition model

The rule: **check-in copies the manifest, not the bytes.**

```
v1.content_refs = [
    {role: primary,   sha256: A, sourceVersionId: v1},
    {role: thumbnail, sha256: T, sourceVersionId: v1, generatedBy: 'pipeline:thumbnailer'},
    {role: extract,   sha256: X, sourceVersionId: v1, generatedBy: 'pipeline:tika'}
]

# Check-in of a metadata-only change (title fix):
v2.content_refs = [
    {role: primary,   sha256: A, sourceVersionId: v1},   ← unchanged
    {role: thumbnail, sha256: T, sourceVersionId: v1},   ← unchanged
    {role: extract,   sha256: X, sourceVersionId: v1}    ← unchanged
]
# Zero bytes copied. Both versions share all renditions.

# Now PUT a new primary rendition on v2:
v2.content_refs = [
    {role: primary,   sha256: B, sourceVersionId: v2},   ← SPLIT — new blob, new source
    {role: thumbnail, sha256: T, sourceVersionId: v1},   ← still shared
    {role: extract,   sha256: X, sourceVersionId: v1}    ← still shared
]
```

**Invariant:** two versions share a rendition iff they have the same
`sha256` for that role. No separate sharing bookkeeping needed —
content-address IS the sharing token.

`DocumentService.attachRendition(...)` mutates a specific version's
manifest without bumping the version — used by pipelines (e.g. a
thumbnail worker) to attach derived renditions after the fact.

## 8. Build & run

### Prerequisites

- **Java 21+** on `$PATH`
- **Maven 3.9+**
- **pnpm** (or npm) for the React UI

### Full build

```bash
git clone https://github.com/geekychris/hitorro-nosqldms.git
cd hitorro-nosqldms

# 1. Build the UI (writes into hitorro-nosqldms-spring-boot/…/static/)
(cd hitorro-nosqldms-web && pnpm install && pnpm build)

# 2. Build + test the Java modules
mvn install
```

**Test summary:** 63 core tests + 5 Spring integration tests = 68 total.

### Run standalone (single-node)

```bash
java -jar hitorro-nosqldms-spring-boot/target/hitorro-nosqldms-spring-boot-0.1.0-app.jar

# then open http://localhost:8090
```

Storage is in-memory by default; data goes away when the process
stops. To persist Lucene data across restarts, keep `dms.home`
pointing at a stable directory (Lucene lives under `${dms.home}/lucene`).

The other five stores (Document / Reference / Folder / ACL / Tag /
Blob) are in-memory only in phase 1 — you'll lose them on restart
until a persistent impl is wired in (see [Extending](#12-extending)).

### Standalone (framework-neutral, no HTTP)

Embed the core inside another Java app or use it from a script:

```java
try (DmsContext ctx = DmsContext.builder()
        .withLucene(Path.of("/tmp/idx"))
        .withTypesDir(Path.of("/etc/mydms/types"))   // optional user overlay
        .build()) {

    DocumentService svc = ctx.documentService();

    CreateRequest req = new CreateRequest();
    req.title = "Ship v0.2";
    req.contentType = "task";
    req.typeName = "task";
    req.typeFields = Map.of("assignee", "alice", "status", "todo");
    req.createdBy = "cli";
    Document v1 = svc.create(req);

    // Later, from anywhere:
    Document head = svc.getHead(v1.canonicalId).orElseThrow();
}
```

### Dev mode (hot-reload the UI)

The UI's `vite dev` server proxies API calls to the backend:

```bash
# Terminal 1 — run the backend on :8090
java -jar hitorro-nosqldms-spring-boot/target/*app.jar

# Terminal 2 — run the UI dev server on :5174 with hot-reload
cd hitorro-nosqldms-web && pnpm dev
# open http://localhost:5174
```

## 9. Configuration

Standard Spring Boot properties. Configure via `application.yml`
(inside the jar), external `application-*.yml`, env vars, or
command-line `--flag=value`.

| Key | Default | Purpose |
|---|---|---|
| `server.port` | `8090` | HTTP port |
| `dms.home` | `${user.home}/.hitorro/dms` | Persistent storage root |
| `dms.lucene-enabled` | `true` | Toggle Lucene index |
| `dms.lucene-dir` | `${dms.home}/lucene` | Override index location |
| `dms.types-dir` | `${dms.home}/types` | User type-def overlay dir (optional) |

Example running on port 9000 with a custom types directory:

```bash
java -jar hitorro-nosqldms-spring-boot-0.1.0-app.jar \
    --server.port=9000 \
    --dms.home=/var/dms \
    --dms.types-dir=/etc/dms/types
```

## 10. Java API

Everything in this section lives in `hitorro-nosqldms-core` and is
framework-independent.

### `DmsContext` — the entry point

Framework-neutral service registry. Mirrors hitorro's
`com.hitorro.util.startupframework.ServiceContext` pattern — one
object owns the wired-up services, hand out via typed accessors.

```java
// Zero-config: all in-memory, no Lucene index.
DmsContext ctx = DmsContext.inMemory();

// Fluent builder — swap any store, add Lucene, add a types overlay:
DmsContext ctx = DmsContext.builder()
        .documentStore(myCustomDocumentStore)   // else: InMemoryDocumentStore
        .blobStore(myS3BlobStore)               // else: InMemoryBlobStore
        .withLucene(Path.of("/var/dms/lucene")) // else: IndexWriter.NOOP
        .withTypesDir(Path.of("/etc/dms/types"))
        .with(MyExtra.class, myExtra)           // register anything else
        .build();

// Accessors:
DocumentService svc     = ctx.documentService();
TypeRegistry    types   = ctx.typeRegistry();
DocumentStore   docs    = ctx.documentStore();
ReferenceStore  refs    = ctx.referenceStore();
FolderStore     folders = ctx.folderStore();
AclStore        acls    = ctx.aclStore();
TagStore        tags    = ctx.tagStore();
BlobStore       blobs   = ctx.blobStore();
IndexWriter     writer  = ctx.indexWriter();
IndexSearcher   search  = ctx.indexSearcher();

MyExtra x = ctx.get(MyExtra.class);              // for user-registered extras

ctx.close();                                     // closes Lucene if attached
```

### `DocumentService`

The orchestrator. Every method that mutates commits to KV, blob store,
and index atomically (from the caller's perspective — errors mid-flow
throw, no partial writes surface).

```java
// Create — mints doc-<uuid> canonical + ver-<uuid> version at 1.0.0
Document create(CreateRequest req) throws IOException;

// Check in a new version. Copy-on-write per rendition.
// bumpKind = MAJOR / MINOR / PATCH / QUALIFIER (default MINOR)
Document checkIn(CheckInRequest req) throws IOException;

// Attach a rendition to a specific EXISTING version — no version bump.
// Used by pipelines that compute derived renditions.
Document attachRendition(String canonicalId, String versionId,
                         String role, String mime, byte[] bytes,
                         String generatedBy, String derivedFromRole) throws IOException;

// Remove one rendition from one version. Blob refcount drops; GC eventually sweeps.
void deleteRendition(String canonicalId, String versionId, String role) throws IOException;

// Fetch bytes of one rendition on one version.
Optional<byte[]> readRendition(String canonicalId, String versionId, String role) throws IOException;

// Soft-delete.
void tombstone(String canonicalId);

// Reads
Optional<Document> getHead(String canonicalId);
Optional<Document> getVersion(String canonicalId, String versionLabel);
Optional<Document> getVersionById(String versionId);
List<Document>    listVersions(String canonicalId);   // insertion order
List<String>      listCanonicals();
```

### `CreateRequest` / `CheckInRequest`

Simple POJOs. Every field is optional except the caller-supplied
required fields (`title`, `canonicalId`, etc.).

```java
CreateRequest c = new CreateRequest();
c.title       = "Spec";
c.body        = "Long body text.";
c.description = "One-liner.";
c.contentType = "task";
c.typeName    = "task";        // defaults to contentType if null
c.typeFields  = Map.of("assignee", "alice", "status", "todo");
c.createdBy   = "user:alice";
c.withRendition("primary", "text/plain", "hello".getBytes());
// c.withRendition(...) adds to the .renditions Map
Document v1 = svc.create(c);

CheckInRequest ci = new CheckInRequest();
ci.canonicalId = v1.canonicalId;
ci.title       = "Spec v2";     // overrides prev head; nulls INHERIT from prev
ci.bumpKind    = VersionLabel.Kind.MINOR;   // default
ci.qualifier   = "beta";        // optional — enters pre-release cycle
ci.typeFields  = Map.of("status", "in-progress");   // merged with prev head
ci.withRendition("primary", "image/jpeg", newBytes);   // replaces primary; other renditions still shared
Document v2 = svc.checkIn(ci);
```

**Merge semantics for `typeFields`:** the new version's fields are
`prev.typeFields ∪ req.typeFields` with request keys taking precedence.
To clear a field entirely, pass it as `null` in the request map. To
keep a field, omit it — it inherits from the previous head.

### `VersionLabel`

Immutable, comparable, parseable.

```java
VersionLabel l = VersionLabel.parse("2.1.3-alpha3+45");
// l.major=2, l.minor=1, l.patch=3, l.qualifier="alpha", l.qualNumber=3, l.build=45
l.isStable();               // false
l.label();                  // "2.1.3-alpha3+45"

// Bump — new label for the next check-in
l.bump(Kind.MAJOR);         // → 3.0.0            (drops qualifier by default)
l.bump(Kind.MINOR);         // → 2.2.0
l.bump(Kind.PATCH);         // → 2.1.4
l.bump(Kind.QUALIFIER);     // → 2.1.3-alpha4
l.bump(Kind.MINOR, "beta"); // → 2.2.0-beta1     (enter pre-release cycle)

// Ordering
VersionLabel.parse("1.0.0").compareTo(VersionLabel.parse("1.0.0-alpha"));   // > 0 (stable beats pre-release)
```

### `TypeRegistry`

Read-only after startup.

```java
TypeRegistry reg = new TypeRegistry();                        // classpath types only
TypeRegistry reg = new TypeRegistry(Path.of("/etc/types"));   // + user overlay

List<TypeDef>  all      = reg.all();
Optional<TypeDef> task  = reg.get("task");
boolean exists          = reg.has("task");

// Validate a typeFields map against a type's required fields.
List<String> errs = reg.validate("task", Map.of("assignee", "alice"));
// errs = ["required field missing: status"]
```

### Storage interfaces

All six are named consistently (`{Entity}Store`) and expose a small
CRUD surface. `DocumentStore` is the biggest:

```java
public interface DocumentStore {
    void putVersion(Document doc);                                    // insert / replace by (canonical, label)
    void setHead(String canonicalId, String versionId);               // atomic head swap
    Optional<Document> getHead(String canonicalId);
    Optional<Document> getVersion(String canonicalId, String label);
    Optional<Document> getVersionById(String versionId);
    List<Document>    listVersions(String canonicalId);
    List<String>      listCanonicals();
    void tombstone(String canonicalId);
    boolean isTombstoned(String canonicalId);
    void purge(String canonicalId);                                    // hard delete
}
```

The other stores are similarly narrow — see the interface files
under [`store/`](../hitorro-nosqldms-core/src/main/java/com/hitorro/dms/store/).

### `BlobStore`

Content-addressed. Same bytes ⇒ same hash ⇒ single stored blob.

```java
Blob        b = blobStore.put(bytes, "image/jpeg");     // computes sha256
String      h = b.sha256;                                // 64 hex chars
Optional<byte[]> read = blobStore.get(h);
Optional<Blob>   meta = blobStore.stat(h);
boolean          has  = blobStore.exists(h);
blobStore.delete(h);                                     // GC after unref
```

### `IndexWriter` / `IndexSearcher`

Sink + read APIs, kept separate for clean substitution. `LuceneIndex`
implements both.

```java
// Sink
indexWriter.indexDocument(doc);      // idempotent — reindex safe
indexWriter.deleteDocument(versionId);
indexWriter.commit();

// Search
List<SearchHit> hits = indexSearcher.search("chris AND status:open", 20);
Map<String,Object> stored = indexSearcher.fetch(versionId);
```

`SearchHit` is a record: `versionId`, `canonicalId`, `versionLabel`,
`title`, `score`.

## 11. REST API

Base URL: `http://<host>:<port>/`  (default `http://localhost:8090`).
All request/response bodies are JSON unless noted.

### Documents

| Method | Path | Purpose | Request body | Response |
|---|---|---|---|---|
| `POST` | `/api/documents` | Create new doc at v1.0.0 | `CreateRequest` | `Document` |
| `GET` | `/api/documents` | List every canonical id | — | `String[]` |
| `GET` | `/api/documents/{id}` | Current head | — | `Document` |
| `DELETE` | `/api/documents/{id}` | Soft-delete (tombstone) | — | `204` |
| `GET` | `/api/documents/{id}/versions` | Version history | — | `Document[]` |
| `GET` | `/api/documents/{id}/versions/{label}` | One specific version | — | `Document` |
| `POST` | `/api/documents/{id}/versions` | Check in a new version | `CheckInRequest` (without canonicalId) | `Document` |

**`CreateRequest` body:**

```json
{
  "title":       "Ship v0.2",
  "body":        "The 0.2 release notes.",
  "description": "One-line summary.",
  "contentType": "task",
  "typeName":    "task",
  "typeFields":  { "assignee":"alice", "status":"todo", "priority":"high" },
  "createdBy":   "user:alice"
}
```

**`CheckInRequest` body:**

```json
{
  "title":       "Ship v0.2 (fixed)",
  "body":        "Updated notes.",
  "bumpKind":    "MINOR",
  "qualifier":   null,
  "typeName":    null,
  "typeFields":  { "status": "in-progress" },
  "modifiedBy":  "user:bob"
}
```

`bumpKind` ∈ `MAJOR`, `MINOR`, `PATCH`, `QUALIFIER`. Fields absent
from the body inherit from the previous head; `typeFields` merges
per-key.

**`Document` response** — see [section 4](#4-data-model).

### Renditions

Copy-on-write per rendition. Same role ⇒ replaces; new role ⇒ appends.

| Method | Path | Purpose | Body |
|---|---|---|---|
| `GET` | `/api/documents/{id}/renditions` | List renditions on head | — |
| `GET` | `/api/documents/{id}/versions/{versionId}/renditions` | List renditions on a specific version | — |
| `GET` | `/api/documents/{id}/renditions/{role}` | Read bytes of rendition on head | — (returns raw bytes with the stored MIME) |
| `GET` | `/api/documents/{id}/versions/{versionId}/renditions/{role}` | Read bytes on a specific version | — |
| `PUT` | `/api/documents/{id}/versions/{versionId}/renditions/{role}` | Attach or replace a rendition | raw bytes (`Content-Type` header sets the MIME) |
| `DELETE` | `/api/documents/{id}/versions/{versionId}/renditions/{role}` | Remove rendition entry | — |

**PUT headers:**
- `Content-Type: image/jpeg` — becomes the stored MIME
- `X-Generated-By: user` (default) or `X-Generated-By: pipeline:<name>` — provenance tag
- `X-Derived-From: primary` — for derived renditions, which role was the source

### References

| Method | Path | Purpose | Body |
|---|---|---|---|
| `GET` | `/api/documents/{id}/references` | Outbound refs | — |
| `GET` | `/api/documents/{id}/references/inbound` | Inbound refs | — |
| `POST` | `/api/documents/{id}/references` | Add ref (from `id`) | `Reference` |
| `DELETE` | `/api/documents/{id}/references/{to}/{kind}` | Remove one ref | — |

**`Reference` body:**

```json
{ "toCanonical":"doc-abc", "toVersion":null, "kind":"cites", "aux":"sect 3.2" }
```

`fromCanonical` is filled in from the URL path.

### ACLs

| Method | Path | Purpose | Body |
|---|---|---|---|
| `GET` | `/api/documents/{id}/acls` | List grants on doc | — |
| `POST` | `/api/documents/{id}/acls` | Grant permission | `Grant` |
| `DELETE` | `/api/documents/{id}/acls/{principal}/{permission}` | Revoke grant | — |

**`Grant` body:**

```json
{ "principal":"user:alice", "permission":"read", "grant":true }
```

Permissions: `read` / `write` / `delete` / `share` / `admin` (any
string is accepted — application defines semantics).

### Folders

Folders are documents (`contentType:"folder"`) plus many-to-many
membership entries.

| Method | Path | Purpose | Body |
|---|---|---|---|
| `GET` | `/api/folders/{folder}/contents` | List folder contents | — |
| `POST` | `/api/folders/{folder}/contents` | Link doc into folder | `{"child":"doc-…","addedBy":"user:alice"}` |
| `DELETE` | `/api/folders/{folder}/contents/{child}` | Unlink | — |
| `GET` | `/api/folders/for-doc/{child}` | List every folder containing `child` | — |

A doc can be in any number of folders. Link/unlink is a 2-KV write;
doc body is never touched.

### Tags

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/documents/{id}/tags` | List tags |
| `POST` | `/api/documents/{id}/tags/{tag}` | Add tag |
| `DELETE` | `/api/documents/{id}/tags/{tag}` | Remove tag |
| `GET` | `/api/tags/{tag}/documents` | List docs with tag |

### Types

Read-only.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/types` | List registered TypeDefs |
| `GET` | `/api/types/{name}` | Fetch one TypeDef |

### Search

Lucene classic query grammar over the primary index.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/search?q=<query>&limit=<n>` | Full-text search |

Response: `SearchHit[]` — each hit has `versionId`, `canonicalId`,
`versionLabel`, `title`, `score`.

**Query examples:**

- `chris` — bare query. Matches title/body/description/typed fields via `_all`.
- `title:chris` — exact-field.
- `body:kubernetes AND tf.status:open` — typed-field filter.
- `type_name:task AND tf.priority:high` — all high-priority tasks.
- `is_head:true` — only current head versions.

### Curl examples

```bash
# Create
curl -X POST http://localhost:8090/api/documents -H 'content-type: application/json' \
  -d '{"title":"Ship v0.2","contentType":"task","typeName":"task",
       "typeFields":{"assignee":"alice","status":"todo","priority":"high"},
       "createdBy":"cli"}'

# Attach a rendition (PUT raw bytes)
CID=doc-abc-…; VID=ver-xyz-…
echo "PRIMARY BYTES" | curl -X PUT --data-binary @- \
     -H 'content-type: text/plain' \
     "http://localhost:8090/api/documents/$CID/versions/$VID/renditions/primary"

# Read rendition
curl "http://localhost:8090/api/documents/$CID/renditions/primary"

# Check-in a new version (metadata-only — content stays shared)
curl -X POST "http://localhost:8090/api/documents/$CID/versions" \
     -H 'content-type: application/json' \
     -d '{"title":"Ship v0.2 (fixed)","bumpKind":"MINOR"}'

# List versions
curl "http://localhost:8090/api/documents/$CID/versions"

# Search
curl 'http://localhost:8090/api/search?q=chris'
```

## 12. Extending

### Add a new document type

Drop a JSON file into your types dir (default `${dms.home}/types/`):

```json
{
  "name": "meeting-note",
  "title": "Meeting note",
  "description": "Notes from a meeting with attendees and action items.",
  "fields": [
    { "name": "attendees", "kind": "array<string>", "label": "Attendees" },
    { "name": "date",      "kind": "date",          "label": "Date", "required": true },
    { "name": "outcome",   "kind": "enum",          "label": "Outcome",
      "choices": ["informational","decision","action-required"] },
    { "name": "action_items", "kind": "text",       "label": "Action items", "rows": 6 }
  ]
}
```

Restart the app; the type appears in the picker, and its fields
render as a dynamic form on create + detail.

### Swap in a persistent storage impl

Implement any of the six store interfaces (say `RocksDBDocumentStore`)
and provide it as a Spring bean:

```java
@Configuration
public class MyStorageConfig {
    @Bean DocumentStore documentStore() { return new RocksDBDocumentStore(myRocksPath); }
    @Bean BlobStore     blobStore()     { return new S3BlobStore(bucket); }
}
```

Spring's `@Bean` overrides win over the auto-configured in-memory
defaults. `DmsContext` is rebuilt using your beans.

Standalone (no Spring):

```java
DmsContext ctx = DmsContext.builder()
    .documentStore(new RocksDBDocumentStore(myRocksPath))
    .blobStore(new S3BlobStore(bucket))
    .withLucene(Path.of("/var/dms/lucene"))
    .build();
```

### Extend the UI type-widget mapping

Edit
[`TypedFieldsForm.tsx`](../hitorro-nosqldms-web/src/components/TypedFieldsForm.tsx)
— the `switch (field.kind)` block. Add a case for a new kind (e.g.
`richtext`) that renders your custom widget component. The value
returned by `onChange` gets stored on `typeFields` verbatim.

## 13. Design notes + tradeoffs

### What's deferred (per the design roadmap)

- **Distribution.** Phase 1 is single-node. Partitioning by
  `canonical_id` and cross-partition back-refs is phase 3.
- **ACL inheritance walk.** Explicit grants work; folder-inherited
  grants are phase 4.
- **MinIO / S3 blob backends.** Interface + KV impl only in phase 5.
- **Blob GC pipeline.** Mark-and-sweep is designed but not implemented.
- **Check-out leases.** Field on `Document` exists but no server-side
  enforcement.

### Explicit tradeoffs

- **No cross-document ACID.** Same-partition (same-doc) ops are atomic.
  Cross-doc ops (references, folder membership when folder is on
  another agent in a distributed deploy) are eventually consistent +
  idempotent. If you need transactional integrity across multiple
  docs, this is not the system.
- **Read-optimized rendition manifest.** Attaching a derived rendition
  requires rewriting the version's `v|` KV entry — not free. Chose
  read-side simplicity over write efficiency here because reads
  dominate for renditions (list them on every doc-detail view).
- **Lucene as sole search.** Range queries + full-text + faceting are
  great; joins across indexes are limited. If you need
  cross-document analytics, project into a warehouse.
- **In-memory phase-1 stores.** Data doesn't survive restart until a
  persistent impl is wired in. Lucene index alone is persistent
  (survives restart) but the authoritative store is not — meaning
  right now, restart = data loss for docs. See [Extending](#12-extending)
  for the RocksDB-swap pattern.

## References

- Design proposal (hitorro-jsontypesystem repo):
  [distributed-dms.md](https://github.com/geekychris/hitorro-jsontypesystem/blob/main/docs/distributed-dms.md)
- Framework-neutral core:
  [`hitorro-nosqldms-core/`](../hitorro-nosqldms-core/)
- Spring Boot runtime:
  [`hitorro-nosqldms-spring-boot/`](../hitorro-nosqldms-spring-boot/)
- React UI:
  [`hitorro-nosqldms-web/`](../hitorro-nosqldms-web/)
