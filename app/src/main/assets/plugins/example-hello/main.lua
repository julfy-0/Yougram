plugin.on_load(function()
    print("[Hello World] plugin loaded")
end)

plugin.on_enable(function()
    print("[Hello World] plugin enabled")
    yougram.ui.notification("Hello World", "The Lua plugin is running!")
end)

plugin.on_disable(function()
    print("[Hello World] plugin disabled")
end)
