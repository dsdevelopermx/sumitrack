using Serilog;
using Serilog.Formatting.Compact;

namespace Sumitrack.Api.Infrastructure.Logging;

public static class SerilogConfiguration
{
    public static void ConfigureSerilog(HostBuilderContext context, LoggerConfiguration config)
    {
        config
            .ReadFrom.Configuration(context.Configuration)
            .Enrich.FromLogContext()
            .Enrich.WithMachineName()
            .WriteTo.Console(new CompactJsonFormatter());
    }
}
