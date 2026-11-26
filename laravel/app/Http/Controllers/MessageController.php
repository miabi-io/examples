<?php

namespace App\Http\Controllers;

use App\Jobs\PublishMessage;
use App\Models\Message;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class MessageController extends Controller
{
    public function index(): View
    {
        return view('messages', [
            'messages' => Message::published()->latest('published_at')->limit(50)->get(),
            'pending' => Message::pending()->count(),
            'hostname' => gethostname(),
            'release' => $this->releaseLine(config('app.miabi')),
        ]);
    }

    /** @param array<string, string> $miabi */
    private function releaseLine(array $miabi): ?string
    {
        if (! isset($miabi['MIABI_RELEASE'])) {
            return null;
        }

        return 'Release '.$miabi['MIABI_RELEASE']
            .(isset($miabi['MIABI_NODE']) ? ' on node '.$miabi['MIABI_NODE'] : '')
            .(isset($miabi['MIABI_REPLICA']) ? ', replica '.$miabi['MIABI_REPLICA'] : '');
    }

    public function store(Request $request): RedirectResponse
    {
        $validated = $request->validate([
            'author' => ['required', 'string', 'max:60'],
            'body' => ['required', 'string', 'max:500'],
        ]);

        PublishMessage::dispatch(Message::create($validated));

        return redirect()->route('messages.index')->with('status', 'Message queued.');
    }
}
