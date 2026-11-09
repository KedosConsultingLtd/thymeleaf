# Kedos builds: publishing and consuming

## What is published

| Artifact | Notes |
|---|---|
| `org.thymeleaf:thymeleaf-parent` | Parent POM (needed to resolve the others) |
| `org.thymeleaf:thymeleaf-lib` | Parent POM (needed to resolve the others) |
| `org.thymeleaf:thymeleaf` | Core engine, with sources and javadoc jars |
| `org.thymeleaf:thymeleaf-spring6` | Spring 6 integration, with sources and javadoc jars |

- **Version:** `<upstream>.KEDOS-<n>`, e.g. `3.1.6.KEDOS-1`. Upstream never uses this, so it can't clash with
  Maven Central.
- **Registry:** GitHub Packages for this repository, `https://maven.pkg.github.com/KedosConsultingLtd/thymeleaf-spring`.

The group id stays `org.thymeleaf`, so an application only changes the version (for example Spring Boot's
`thymeleaf.version` property) and doesn't need dependency exclusions.

## Releasing a build

```bash
git tag 3.1.6.KEDOS-1 <commit>      # the tag is the version
git push origin 3.1.6.KEDOS-1
```

The `kedos-publish` workflow (`.github/workflows/kedos-publish.yml`) then:

1. sets the version from the tag at build time (committed POMs keep `3.1.6.RELEASE`);
2. builds and runs the core and Spring 6 test suites;
3. runs both suites again with static-run coalescing forced on;
4. pushes the four artifacts with `deploy:deploy-file`.

Upstream's Maven Central publishing and GPG signing are never invoked.

Published versions are immutable. To fix something, release `KEDOS-2`.

## Consuming (e.g. a Spring Boot application)

1. **Authentication.** GitHub Packages needs a token with `read:packages`, even for public repositories.
   Create a fine-grained or classic PAT (or a machine user's token) and store it as a masked CI variable.

   `~/.m2/settings.xml`, or a settings file used by CI:

   ```xml
   <settings>
     <servers>
       <server>
         <id>kedos-thymeleaf</id>
         <username>${env.KEDOS_PACKAGES_USER}</username>
         <password>${env.KEDOS_PACKAGES_TOKEN}</password>
       </server>
     </servers>
   </settings>
   ```

2. **Repository and version** in the application POM:

   ```xml
   <properties>
     <thymeleaf.version>3.1.6.KEDOS-1</thymeleaf.version>  <!-- Boot's BOM applies it to thymeleaf and thymeleaf-spring6 -->
   </properties>

   <repositories>
     <repository>
       <id>kedos-thymeleaf</id>
       <url>https://maven.pkg.github.com/KedosConsultingLtd/thymeleaf-spring</url>
       <releases><enabled>true</enabled></releases>
       <snapshots><enabled>false</enabled></snapshots>
     </repository>
   </repositories>
   ```

3. **Check it resolved.** `mvn dependency:tree | grep thymeleaf` should show `3.1.6.KEDOS-1` for both artifacts.

## Turning the optimisations on

Nothing changes until you opt in. Each feature is a dialect bean (Spring Boot adds every `IDialect` bean to the
engine).

| Feature | How | Notes |
|---|---|---|
| `ExpressionUtils` cache, SpEL accessor caching, link building | Always on | Behaviour identical to upstream (see `security-audit.md`) |
| Parse-time whitespace collapsing | `@Bean WhitespaceCollapsingDialect` | Remove any per-render whitespace post-processor. Output differs from upstream only in whitespace |
| Static-run coalescing | `@Bean StaticRunCoalescingDialect` | **Switches itself off (logging a warning) when any pre/post-processor is configured, or any dialect with element model processors is not listed as trusted.** Review your own dialects' model processors, then pass them: `new StaticRunCoalescingDialect(Set.of(MyDialect.class))`. Never trust layout/decorator dialects |

After enabling, check the application log for `Static-run coalescing is disabled: …`. If it appears, the reason
is given there.
