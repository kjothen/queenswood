-- .nvim.lua
local lspconfig = require("lspconfig")

-- Customize yamlls settings
lspconfig.yamlls.setup({
	settings = {
		yaml = {
			customTags = {
				"!keyword",
				"!include",
				"!port",
				"!profile mapping",

				"!pulsar/crypto-failure-action",
				"!pulsar/message-id",
				"!pulsar/schema",
				"!pulsar/subscription-type",

				"!system/component mapping",
				"!system/local-ref",
				"!system/ref",
				"!system/required-component",
			},
		},
	},
})
