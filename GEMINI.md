# Deepwither Project Context

## Architecture Mandates
- **Modular Monolith:** New features must be implemented as `IModule` within `com.lunar_prototype.deepwither.modules.[feature]` and registered in `DeepwitherBootstrap`.
- **Dependency Injection:** 
    - Use the custom `ServiceManager` and `@DependsOn` annotation for initialization order.
    - Managers must implement `IManager` and be registered in `Deepwither.setupManagers()`.
    - Use `ServiceContainer` for constructor injection in modules.
- **Initialization:** Direct logic in `onEnable` is prohibited. All initialization logic must reside in the `init()` method of a Manager class.

## Coding Standards
- **Facade Access:** Access managers and features primarily through the `DW` static facade class (e.g., `DW.db()`, `DW.stats(player)`).
- **Text Formatting:** Use Paper's **Adventure Component API** (`Component`, `NamedTextColor`, etc.). Never use `org.bukkit.ChatColor` or legacy section symbols (§).
- **Database:** Always use `DW.db()` (`IDatabaseManager`) for database operations. Direct connection handling is discouraged.
- **Deepwither Class:** Avoid adding getters directly to `Deepwither.java`.

## Technical Environment
- **Build System:** Use IntelliJ IDEA's internal Maven. Do not attempt to run `mvn` via shell commands as it is not installed in the environment.
