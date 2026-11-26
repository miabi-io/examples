<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{{ config('app.name') }}</title>
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="min-h-screen bg-stone-50 text-stone-900 antialiased dark:bg-stone-950 dark:text-stone-100">
    <main class="mx-auto max-w-2xl px-4 py-12">
        <header class="mb-8">
            <h1 class="text-3xl font-semibold">{{ config('app.name') }}</h1>
            <p class="mt-1 text-sm text-stone-500">
                Laravel {{ app()->version() }} · PHP {{ PHP_VERSION }} · served by <code>{{ $hostname }}</code>
            </p>
            @if ($release)
                <p class="mt-1 text-sm text-stone-500">{{ $release }}</p>
            @endif
        </header>

        @if (session('status'))
            <p class="mb-4 rounded-md bg-emerald-100 px-4 py-2 text-sm text-emerald-900 dark:bg-emerald-900/40 dark:text-emerald-100">{{ session('status') }}</p>
        @endif

        <form method="POST" action="{{ route('messages.store') }}" class="mb-10 space-y-3 rounded-lg border border-stone-200 bg-white p-4 dark:border-stone-800 dark:bg-stone-900">
            @csrf
            <input name="author" value="{{ old('author') }}" placeholder="Your name" maxlength="60" required
                   class="w-full rounded-md border border-stone-300 bg-transparent px-3 py-2 dark:border-stone-700">
            <textarea name="body" rows="3" placeholder="Leave a message" maxlength="500" required
                      class="w-full rounded-md border border-stone-300 bg-transparent px-3 py-2 dark:border-stone-700">{{ old('body') }}</textarea>
            @if ($errors->any())
                <p class="text-sm text-red-600">{{ $errors->first() }}</p>
            @endif
            <button class="rounded-md bg-stone-900 px-4 py-2 text-sm font-medium text-white dark:bg-stone-100 dark:text-stone-900">Sign the guestbook</button>
        </form>

        @if ($pending > 0)
            <p class="mb-4 text-sm text-amber-700 dark:text-amber-400">{{ $pending }} message(s) waiting for the queue worker.</p>
        @endif

        <ul class="space-y-4">
            @forelse ($messages as $message)
                <li class="rounded-lg border border-stone-200 bg-white p-4 dark:border-stone-800 dark:bg-stone-900">
                    <p class="whitespace-pre-line">{{ $message->body }}</p>
                    <p class="mt-2 text-xs text-stone-500">{{ $message->author }} · {{ $message->published_at->diffForHumans() }}</p>
                </li>
            @empty
                <li class="text-stone-500">No messages yet. Be the first.</li>
            @endforelse
        </ul>
    </main>
</body>
</html>
