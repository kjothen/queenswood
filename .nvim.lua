-- .nvim.lua
local lspconfig = require("lspconfig")

-- Customize yamlls settings
lspconfig.yamlls.setup({
	settings = {
		yaml = {
			customTags = {
				"!include",
				"!pubsub/crypto-failure-action",
				"!pubsub/message-id",
				"!pubsub/schema",
				"!pubsub/subscription-type",
				"!system/component mapping",
				"!system/local-ref",
				"!system/ref",
				"!system/required-component",
				"!profile mapping",
				"!port",
			},
		},
	},
})
