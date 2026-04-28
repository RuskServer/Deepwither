use zed_extension_api as zed;

struct DeepwitherExtension;

impl zed::Extension for DeepwitherExtension {
    fn new() -> Self {
        Self
    }

    fn language_server_command(
        &mut self,
        _id: &zed::LanguageServerId,
        _worktree: &zed::Worktree,
    ) -> zed::Result<zed::Command> {
        Ok(zed::Command {
            command: "/Users/RuskDev/document/Deepwither/deepwither-zed-extension/server/target/release/deepwither-lsp".to_string(),
            args: vec![],
            env: Default::default(),
        })
    }
}

zed::register_extension!(DeepwitherExtension);
