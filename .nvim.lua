-- .nvim.lua
local lspconfig = require("lspconfig")

-- Customize yamlls settings
lspconfig.yamlls.setup({
	settings = {
		yaml = {
			customTags = {
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
