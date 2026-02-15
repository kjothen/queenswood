-- .nvim.lua
---@diagnostic disable: undefined-global
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

				"!pulsar/access-mode",
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

-- Format Clojure files with zprint on save (using fast babashka script)
vim.api.nvim_create_autocmd("BufWritePost", {
	pattern = { "*.clj", "*.cljs", "*.cljc", "*.edn" },
	callback = function()
		local file = vim.fn.expand("%:p")
		local project_root = vim.fn.getcwd()
		local script = project_root .. "/.zprint-format.bb"
		if vim.fn.filereadable(script) == 1 then
			vim.fn.system(script .. " " .. vim.fn.shellescape(file))
			vim.cmd("checktime") -- Reload the file if it changed
		end
	end,
	desc = "Format Clojure files with zprint via babashka",
})
