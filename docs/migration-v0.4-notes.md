# v0.3 → v0.4 Migration Notes (working draft)

각 feature task 가 자기 변경사항을 한 줄씩 append 한다. task 24 가 종합본 작성.

## DSL changes

## Generated SDK shape changes

- Kotlin `sealed class` annotated with `@JsonTypeInfo(use=NAME, property="…")` is now emitted as a TypeScript discriminated union (`type Shape = ({ kind: 'circle' } & Circle) | …`) instead of requiring a manual nullable-field DTO workaround.

## Annotations newly recognized

## Breaking changes
